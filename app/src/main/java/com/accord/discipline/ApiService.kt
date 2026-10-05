package com.accord.discipline

import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {

    // ── Uploads ───────────────────────────────────────────────────────────
    @Multipart
    @POST("uploads/avatars")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UploadResponse

    @Multipart
    @POST("uploads/rewards")
    suspend fun uploadRewardImage(@Part file: MultipartBody.Part): UploadResponse

    @Multipart
    @POST("uploads/habits")
    suspend fun uploadHabitImage(@Part file: MultipartBody.Part): UploadResponse

    @DELETE("uploads/{context}/{filename}")
    suspend fun deleteFile(@Path("context") context: String, @Path("filename") filename: String): SuccessResponse

    // ── Auth ──────────────────────────────────────────────────────────────
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    // ── Users ─────────────────────────────────────────────────────────────
    @GET("users/me")
    suspend fun getMe(): User

    @POST("users/pair")
    suspend fun pair(@Body request: PairRequest): PairResponse

    @POST("users/unpair")
    suspend fun unpair(@Body request: UnpairRequest = UnpairRequest()): SuccessResponse

    // ── Habits ────────────────────────────────────────────────────────────
    @GET("habits")
    suspend fun getHabits(): List<Habit>

    @GET("habits/history")
    suspend fun getHabitHistory(): List<HabitCompletion>

    @GET("habits/partner")
    suspend fun getPartnerHabits(): List<Habit>

    @POST("habits")
    suspend fun createHabit(@Body request: CreateHabitRequest): Habit

    @PUT("habits/{id}")
    suspend fun updateHabit(@Path("id") id: String, @Body request: CreateHabitRequest): Habit

    @DELETE("habits/{id}")
    suspend fun deleteHabit(@Path("id") id: String): SuccessResponse

    @POST("habits/reorder")
    suspend fun reorderHabits(@Body habitIds: List<String>): SuccessResponse

    @POST("habits/{id}/complete")
    suspend fun completeHabit(@Path("id") id: String, @Body request: CompleteHabitRequest = CompleteHabitRequest()): CompleteHabitResponse

    @POST("habits/partner/{id}/complete")
    suspend fun completePartnerHabit(@Path("id") id: String, @Body request: CompleteHabitRequest = CompleteHabitRequest()): CompleteHabitResponse

    @POST("habits/{id}/uncomplete")
    suspend fun uncompleteHabit(@Path("id") id: String, @Body request: CompleteHabitRequest = CompleteHabitRequest()): CompleteHabitResponse

    @POST("habits/{id}/reset-task")
    suspend fun resetTask(@Path("id") id: String): CompleteHabitResponse

    @POST("habits/partner/{id}/uncomplete")
    suspend fun uncompletePartnerHabit(@Path("id") id: String, @Body request: CompleteHabitRequest = CompleteHabitRequest()): CompleteHabitResponse

    @GET("habits/submissions/pending")
    suspend fun getPendingSubmissions(): List<HabitSubmission>

    @GET("habits/submissions/all")
    suspend fun getAllSubmissions(): List<HabitSubmission>

    @FormUrlEncoded
    @POST("habits/{id}/submit")
    suspend fun submitHabitPhoto(
        @Path("id") id: String,
        @Field("photo_url") photoUrl: String,
        @Field("date") date: String? = null,
    ): SuccessResponse

    @POST("habits/submissions/{id}/approve")
    suspend fun approveSubmission(
        @Path("id") id: String,
        @Query("date") date: String? = null,
        @Body request: CompleteHabitRequest = CompleteHabitRequest(),
    ): CompleteHabitResponse

    @POST("habits/submissions/{id}/reject")
    suspend fun rejectSubmission(@Path("id") id: String): SuccessResponse

    @DELETE("habits/submissions/{id}")
    suspend fun deleteSubmission(@Path("id") id: String): SuccessResponse

    // ── Check-ins ────────────────────────────────────────────────────────
    @GET("checkins")
    suspend fun getCheckIns(): List<MonthlyCheckIn>

    @POST("checkins")
    suspend fun submitCheckIn(@Body request: MonthlyCheckInRequest): MonthlyCheckIn

    // ── Rewards ───────────────────────────────────────────────────────────
    @GET("rewards")
    suspend fun getRewards(): List<Reward>

    @POST("rewards")
    suspend fun createReward(@Body request: CreateRewardRequest): Reward

    @PUT("rewards/{id}")
    suspend fun updateReward(@Path("id") id: String, @Body request: CreateRewardRequest): Reward

    @DELETE("rewards/{id}")
    suspend fun deleteReward(@Path("id") id: String): SuccessResponse

    @POST("rewards/reorder")
    suspend fun reorderRewards(@Body rewardIds: List<String>): SuccessResponse

    @POST("rewards/{id}/redeem")
    suspend fun redeemReward(@Path("id") id: String): RedeemResponse

    @POST("rewards/{id}/reset")
    suspend fun resetReward(@Path("id") id: String): SuccessResponse

    // ── Punishments ───────────────────────────────────────────────────────
    @GET("punishments")
    suspend fun getPunishments(): List<Punishment>

    @POST("punishments")
    suspend fun createPunishment(@Body request: CreatePunishmentRequest): Punishment

    @PUT("punishments/{id}")
    suspend fun updatePunishment(@Path("id") id: String, @Body request: CreatePunishmentRequest): Punishment

    @DELETE("punishments/{id}")
    suspend fun deletePunishment(@Path("id") id: String): SuccessResponse

    @POST("punishments/reorder")
    suspend fun reorderPunishments(@Body punishmentIds: List<String>): SuccessResponse

    @POST("punishments/{id}/assign")
    suspend fun assignPunishment(
        @Path("id") id: String,
        @Body request: AssignPunishmentRequest
    ): SuccessResponse

    @GET("punishments/assigned")
    suspend fun getAssignedPunishments(): List<AssignedPunishment>

    @POST("punishments/assignments/{id}/complete")
    suspend fun completePunishment(@Path("id") id: String): SuccessResponse

    // ── Points ────────────────────────────────────────────────────────────
    @POST("points/adjust")
    suspend fun adjustPoints(@Body request: AdjustPointsRequest): AdjustPointsResponse

    // ── Stats ─────────────────────────────────────────────────────────────
    @GET("stats")
    suspend fun getStats(): Stats

    // ── Admin / Server Management ─────────────────────────────────────────
    @GET("admin/users")
    suspend fun getAdminUsers(): List<User>

    @PUT("admin/users/{id}")
    suspend fun updateAdminUser(
        @Path("id") id: String,
        @Body request: UpdateUserRequest
    ): User

    @POST("admin/users/{id}/password")
    suspend fun changeAdminUserPassword(
        @Path("id") id: String,
        @Body request: ChangePasswordRequest
    ): SuccessResponse

    @DELETE("admin/users/{id}")
    suspend fun deleteAdminUser(@Path("id") id: String): SuccessResponse
}
