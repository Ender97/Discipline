package com.accord.discipline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "discipline_prefs")

object PrefsKeys {
    val TOKEN = stringPreferencesKey("auth_token")
    val SERVER_URL = stringPreferencesKey("server_url")
    val OFFLINE_MODE = androidx.datastore.preferences.core.booleanPreferencesKey("offline_mode")
    val DARK_MODE = stringPreferencesKey("dark_mode")
    val DYNAMIC_COLOR = androidx.datastore.preferences.core.booleanPreferencesKey("dynamic_color")
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val SAVED_USERNAME = stringPreferencesKey("saved_username")
    val SAVED_PASSWORD = stringPreferencesKey("saved_password")
    val SAVED_USER_JSON = stringPreferencesKey("saved_user_json")
    val SELECTED_PARTNER_ID = stringPreferencesKey("selected_partner_id")
    val BACKGROUND_SYNC = androidx.datastore.preferences.core.booleanPreferencesKey("background_sync")
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideGson(): Gson {
        val booleanAdapter = object : TypeAdapter<Boolean>() {
            override fun write(out: JsonWriter, value: Boolean?) {
                out.value(value)
            }
            override fun read(`in`: JsonReader): Boolean? {
                return when (val token = `in`.peek()) {
                    JsonToken.BOOLEAN -> `in`.nextBoolean()
                    JsonToken.NUMBER -> `in`.nextInt() != 0
                    JsonToken.STRING -> `in`.nextString().toBoolean()
                    JsonToken.NULL -> {
                        `in`.nextNull()
                        null
                    }
                    else -> throw IllegalStateException("Expected BOOLEAN, NUMBER or STRING but was $token")
                }
            }
        }
        return GsonBuilder()
            .registerTypeAdapter(Boolean::class.java, booleanAdapter)
            .registerTypeAdapter(Boolean::class.javaPrimitiveType, booleanAdapter)
            .create()
    }

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    /**
     * Auth interceptor: reads the saved token and server URL from DataStore on each request.
     * This means we don't need to recreate Retrofit when the user logs in/out —
     * the interceptor always picks up the latest values.
     */
    @Provides @Singleton
    fun provideOkHttpClient(dataStore: DataStore<Preferences>): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val prefs = runBlocking { dataStore.data.first() }
            val token = prefs[PrefsKeys.TOKEN]
            val partnerId = prefs[PrefsKeys.SELECTED_PARTNER_ID]

            val builder = chain.request().newBuilder()
            if (token != null) {
                builder.addHeader("Authorization", "Bearer $token")
            }
            if (!partnerId.isNullOrBlank()) {
                builder.addHeader("X-Partner-Id", partnerId)
            }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Dynamic base URL: reads the saved server URL from DataStore on each request.
     * Users can change the Pi's IP in Settings without reinstalling.
     */
    @Provides @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson, dataStore: DataStore<Preferences>): Retrofit {
        val dynamicBaseUrlInterceptor = Interceptor { chain ->
            val serverUrl = runBlocking {
                dataStore.data.first()[PrefsKeys.SERVER_URL]?.trim() ?: "10.0.2.2"
            }
            
            // Standardize URL: ensure we have scheme and port
            var fullUrl = serverUrl
            if (fullUrl.isBlank()) fullUrl = "10.0.2.2"
            if (!fullUrl.startsWith("http")) fullUrl = "http://$fullUrl"
            if (!fullUrl.substringAfter("://").contains(":")) fullUrl = "$fullUrl:3000"
            
            val baseUrl = fullUrl.toHttpUrlOrNull() ?: "http://10.0.2.2:3000/".toHttpUrlOrNull()!!
            
            val originalRequest = chain.request()
            val newUrl = originalRequest.url.newBuilder()
                .scheme(baseUrl.scheme)
                .host(baseUrl.host)
                .port(baseUrl.port)
                .build()

            chain.proceed(originalRequest.newBuilder().url(newUrl).build())
        }

        // We use a placeholder base URL; the interceptor above replaces it per-request
        return Retrofit.Builder()
            .baseUrl("http://localhost:3000/")
            .client(
                okHttpClient.newBuilder()
                    .addInterceptor(dynamicBaseUrlInterceptor)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}
