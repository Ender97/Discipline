package com.accord.discipline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class TaskDueDateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TaskDueDateWorkerEntryPoint {
        fun apiService(): ApiService
        fun dataStore(): DataStore<Preferences>
        fun taskDueDateChecker(): TaskDueDateChecker
        fun gson(): com.google.gson.Gson
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            TaskDueDateWorkerEntryPoint::class.java
        )
        val api = entryPoint.apiService()
        val dataStore = entryPoint.dataStore()
        val checker = entryPoint.taskDueDateChecker()
        val gson = entryPoint.gson()

        val prefs = dataStore.data.first()
        val isOffline = prefs[PrefsKeys.OFFLINE_MODE] ?: false
        val token = prefs[PrefsKeys.TOKEN]

        val habitsList = mutableListOf<Habit>()
        val historyList = mutableListOf<HabitCompletion>()

        if (!isOffline && !token.isNullOrBlank()) {
            try {
                val habits = api.getHabits()
                val partnerHabits = try { api.getPartnerHabits() } catch (e: Exception) { emptyList() }
                val history = try { api.getHabitHistory() } catch (e: Exception) { emptyList() }
                habitsList.addAll(habits)
                habitsList.addAll(partnerHabits)
                historyList.addAll(history)
            } catch (e: Exception) {
                loadFromOfflineCache(gson, habitsList, historyList)
            }
        } else {
            loadFromOfflineCache(gson, habitsList, historyList)
        }

        checker.checkTaskDueDates(applicationContext, habitsList, historyList)
        return Result.success()
    }

    private fun loadFromOfflineCache(
        gson: com.google.gson.Gson,
        habitsList: MutableList<Habit>,
        historyList: MutableList<HabitCompletion>
    ) {
        try {
            val sp = applicationContext.getSharedPreferences("offline", Context.MODE_PRIVATE)
            val habitsJson = sp.getString("habits", "[]")
            val historyJson = sp.getString("history", "[]")

            if (!habitsJson.isNullOrBlank()) {
                val habits = gson.fromJson(habitsJson, Array<Habit>::class.java).toList()
                habitsList.addAll(habits)
            }
            if (!historyJson.isNullOrBlank()) {
                val history = gson.fromJson(historyJson, Array<HabitCompletion>::class.java).toList()
                historyList.addAll(history)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
