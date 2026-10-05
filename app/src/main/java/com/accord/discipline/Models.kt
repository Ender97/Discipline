package com.accord.discipline

import com.google.gson.annotations.SerializedName

// ─── Auth ───────────────────────────────────────────────────────────────────

data class RegisterRequest(
    val username: String,
    val password: String,
    val role: String // "dominant" | "submissive"
)

data class LoginRequest(val username: String, val password: String)

data class AuthResponse(
    val token: String,
    val user: User
)

// ─── Users ──────────────────────────────────────────────────────────────────

data class User(
    val id: String = "",
    val username: String = "",
    val role: String = "submissive",
    val points: Int = 0,
    @SerializedName(value = "pair_code", alternate = ["pairCode"])
    val pairCode: String? = null,
    @SerializedName(value = "avatar_url", alternate = ["avatarUrl"])
    val avatarUrl: String? = null,
    val partners: List<Partner> = emptyList()
) {
    val partner: Partner? get() = partners.firstOrNull()
    val partnerId: String? get() = partner?.id
}

data class Partner(
    val id: String = "",
    val username: String = "",
    val role: String = "dominant",
    val points: Int = 0,
    @SerializedName(value = "pair_code", alternate = ["pairCode"])
    val pairCode: String? = null,
    @SerializedName(value = "partner_id", alternate = ["partnerId"])
    val partnerId: String? = null,
    @SerializedName(value = "avatar_url", alternate = ["avatarUrl"])
    val avatarUrl: String? = null
)

data class PairRequest(val pairCode: String)
data class PairResponse(val success: Boolean, val partner: Partner?)
data class UnpairRequest(val partnerId: String? = null)

// ─── Admin / Server Management ──────────────────────────────────────────────

data class UpdateUserRequest(
    val username: String? = null,
    val role: String? = null,
    val points: Int? = null,
    @SerializedName("partner_id")
    val partnerId: String? = null
)

data class ChangePasswordRequest(
    val password: String
)

// ─── Habits ─────────────────────────────────────────────────────────────────

data class Habit(
    val id: String = "",
    @SerializedName(value = "owner_id", alternate = ["ownerId"])
    val ownerId: String = "",
    @SerializedName(value = "assigned_to", alternate = ["assignedTo"])
    val assignedTo: String? = null,
    val title: String = "",
    val description: String? = null,
    val frequency: String = "daily",
    @SerializedName(value = "weekly_target_count", alternate = ["weeklyTargetCount"])
    val weeklyTargetCount: Int = 1,
    @SerializedName(value = "points_reward", alternate = ["pointsReward"])
    val pointsReward: Int = 0,
    @SerializedName(value = "points_penalty", alternate = ["pointsPenalty"])
    val pointsPenalty: Int = 0,
    @SerializedName(value = "penalty_punishment_id", alternate = ["penaltyPunishmentId"])
    val penaltyPunishmentId: String? = null,
    @SerializedName(value = "last_penalty_reset_date", alternate = ["lastPenaltyResetDate"])
    val lastPenaltyResetDate: String? = null, // YYYY-MM-DD
    @SerializedName(value = "due_date", alternate = ["dueDate"])
    val dueDate: String? = null, // YYYY-MM-DD
    @SerializedName(value = "requires_photo", alternate = ["requiresPhoto"])
    val requiresPhoto: Boolean = false,
    @SerializedName(value = "is_nsfw", alternate = ["isNsfw"])
    val isNsfw: Boolean = false,
    @SerializedName(value = "is_positive", alternate = ["isPositive"])
    val isPositive: Boolean = true,
    @SerializedName(value = "is_paused", alternate = ["isPaused"])
    val isPaused: Boolean = false,
    val completed: Boolean = false,
    val order: Int = 0,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: Long = 0L
)

data class CreateHabitRequest(
    val title: String,
    val description: String? = null,
    val frequency: String,
    @SerializedName("weekly_target_count")
    val weeklyTargetCount: Int = 1,
    @SerializedName("points_reward")
    val pointsReward: Int = 0,
    @SerializedName("points_penalty")
    val pointsPenalty: Int = 0,
    @SerializedName("penalty_punishment_id")
    val penaltyPunishmentId: String? = null,
    @SerializedName("last_penalty_reset_date")
    val lastPenaltyResetDate: String? = null,
    @SerializedName("due_date")
    val dueDate: String? = null,
    @SerializedName("requires_photo")
    val requiresPhoto: Boolean = false,
    @SerializedName("is_nsfw")
    val isNsfw: Boolean = false,
    @SerializedName("is_positive")
    val isPositive: Boolean = true,
    @SerializedName("is_paused")
    val isPaused: Boolean = false,
    val order: Int = 0,
    @SerializedName("assigned_to")
    val assignedTo: String? = null
)

data class CompleteHabitResponse(val success: Boolean, val points: Int)

data class CompleteHabitRequest(
    val date: String? = null // YYYY-MM-DD
)

data class SubmitPhotoRequest(
    @SerializedName("photo_url")
    val photoUrl: String
)

// ─── Rewards ────────────────────────────────────────────────────────────────

data class Reward(
    val id: String = "",
    @SerializedName(value = "owner_id", alternate = ["ownerId"])
    val ownerId: String = "",
    @SerializedName(value = "assigned_to", alternate = ["assignedTo"])
    val assignedTo: String? = null,
    val title: String = "",
    val description: String? = null,
    val cost: Int = 0,
    @SerializedName(value = "image_url", alternate = ["imageUrl"])
    val imageUrl: String? = null,
    val type: String = "recurring", // "recurring" | "one_time"
    @SerializedName(value = "redeemed", alternate = ["isRedeemed"])
    val redeemed: Boolean = false,
    val order: Int = 0,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: Long = 0L
)

data class CreateRewardRequest(
    val title: String,
    val description: String? = null,
    val cost: Int,
    @SerializedName("image_url")
    val imageUrl: String? = null,
    val type: String = "recurring",
    val order: Int = 0,
    @SerializedName("assigned_to")
    val assignedTo: String? = null
)

data class RedeemResponse(val success: Boolean, val points: Int)

// ─── Punishments ────────────────────────────────────────────────────────────

data class Punishment(
    val id: String = "",
    @SerializedName(value = "owner_id", alternate = ["ownerId"])
    val ownerId: String = "",
    @SerializedName(value = "assigned_to", alternate = ["assignedTo"])
    val assignedTo: String? = null,
    val title: String = "",
    val description: String? = null,
    val order: Int = 0,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: Long = 0L
)

data class CreatePunishmentRequest(
    val title: String,
    val description: String? = null,
    val order: Int = 0,
    @SerializedName("assigned_to")
    val assignedTo: String? = null
)

data class AssignPunishmentRequest(
    @SerializedName("assign_to_id")
    val assignToId: String? = null,
    val reason: String? = null
)

data class AssignedPunishment(
    val id: String = "",
    @SerializedName(value = "punishment_id", alternate = ["punishmentId"])
    val punishmentId: String = "",
    @SerializedName(value = "assigned_to", alternate = ["assignedTo"])
    val assignedTo: String = "",
    @SerializedName(value = "assigned_by", alternate = ["assignedBy"])
    val assignedBy: String = "",
    val reason: String? = null,
    val completed: Boolean = false,
    @SerializedName(value = "assigned_at", alternate = ["assignedAt"])
    val assignedAt: Long = 0L,
    val title: String = "",
    val description: String? = null
)

// ─── Points ─────────────────────────────────────────────────────────────────

data class AdjustPointsRequest(
    val amount: Int,
    val reason: String? = null,
    @SerializedName("user_id")
    val userId: String? = null
)

data class AdjustPointsResponse(val success: Boolean, val userId: String, val points: Int)

// ─── Stats ──────────────────────────────────────────────────────────────────

data class Stats(
    val totalHabits: Int,
    val totalCompletionsLast30Days: Int,
    val currentPoints: Int,
    val streak: Int,
    val completionsByDay: List<DayCompletion>,
    val recentCompletions: List<RecentCompletion>
)

data class DayCompletion(val date: String, val count: Int)

data class RecentCompletion(
    val habitId: String,
    val completedAt: Long,
    val title: String,
    val frequency: String
)

// ─── Generic ────────────────────────────────────────────────────────────────

data class SuccessResponse(val success: Boolean)

data class HabitCompletion(
    @SerializedName(value = "habit_id", alternate = ["habitId"])
    val habitId: String,
    val date: String // YYYY-MM-DD
)

data class UploadResponse(
    val url: String
)

data class HabitSubmission(
    val id: String = "",
    @SerializedName(value = "habit_id", alternate = ["habitId"])
    val habitId: String = "",
    @SerializedName(value = "user_id", alternate = ["userId"])
    val userId: String = "",
    val username: String = "",
    val habitTitle: String = "",
    val date: String = "",
    @SerializedName(value = "photo_url", alternate = ["photoUrl"])
    val photoUrl: String = "",
    val status: String = "pending",
    @SerializedName(value = "submitted_at", alternate = ["submittedAt"])
    val submittedAt: Long = 0L,
    @SerializedName(value = "is_nsfw", alternate = ["isNsfw"])
    val isNsfw: Boolean = false
)

data class MonthlyCheckIn(
    val id: String = "",
    val month: String = "", // YYYY-MM
    @SerializedName(value = "user_id", alternate = ["userId"])
    val userId: String = "",
    val username: String = "",
    val reflections: String = "",
    @SerializedName(value = "goal_progress", alternate = ["goalProgress"])
    val goalProgress: String = "",
    val rating: Int = 5, // 1-5
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: Long = 0L
)

data class MonthlyCheckInRequest(
    val month: String,
    val reflections: String,
    @SerializedName(value = "goal_progress", alternate = ["goalProgress"])
    val goalProgress: String,
    val rating: Int
)

// ─── WebSocket events ───────────────────────────────────────────────────────

data class WsEvent(
    val type: String,
    // Fields vary by type; parse generically in WebSocketManager
    val habitId: String? = null,
    val habit: Habit? = null,
    val reward: Reward? = null,
    val rewardId: String? = null,
    val rewardTitle: String? = null,
    val punishment: Punishment? = null,
    val punishmentId: String? = null,
    val assignmentId: String? = null,
    val assignedTo: String? = null,
    val reason: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val habitTitle: String? = null,
    val points: Int? = null,
    val avatarUrl: String? = null,
    val amount: Int? = null,
    val partnerId: String? = null
)

// ─── Helpers ────────────────────────────────────────────────────────────────

fun isImageUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val trimmed = url.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
           trimmed.startsWith("https://", ignoreCase = true) ||
           trimmed.startsWith("content://", ignoreCase = true) ||
           trimmed.startsWith("file://", ignoreCase = true) ||
           trimmed.startsWith("/") ||
           trimmed.startsWith("uploads/", ignoreCase = true) ||
           trimmed.endsWith(".jpg", ignoreCase = true) ||
           trimmed.endsWith(".jpeg", ignoreCase = true) ||
           trimmed.endsWith(".png", ignoreCase = true) ||
           trimmed.endsWith(".webp", ignoreCase = true) ||
           trimmed.endsWith(".gif", ignoreCase = true)
}

