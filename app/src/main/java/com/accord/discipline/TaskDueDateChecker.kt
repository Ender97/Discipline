package com.accord.discipline

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskDueDateChecker @Inject constructor(
    private val notificationHelper: NotificationHelper
) {
    fun checkTaskDueDates(context: Context, habits: List<Habit>, history: List<HabitCompletion> = emptyList()) {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val prefs = context.getSharedPreferences("task_due_notifications", Context.MODE_PRIVATE)
        val sentKeys = prefs.getStringSet("sent_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        var updated = false

        habits.forEach { habit ->
            if (habit.frequency == "task" && !habit.isPaused && !habit.dueDate.isNullOrBlank()) {
                val isCompleted = habit.completed || history.any { it.habitId == habit.id }
                if (!isCompleted) {
                    try {
                        val dueLocalDate = LocalDate.parse(habit.dueDate)
                        val daysUntil = ChronoUnit.DAYS.between(today, dueLocalDate)
                        val formattedDue = dueLocalDate.format(DateTimeFormatter.ofPattern("MMM d"))

                        val triple = when {
                            daysUntil == 7L -> Triple(
                                "task_due_7_${habit.id}_$todayStr",
                                "Task Due in 1 Week ⏳",
                                "'${habit.title}' is due in 7 days ($formattedDue)"
                            )
                            daysUntil == 1L -> Triple(
                                "task_due_1_${habit.id}_$todayStr",
                                "Task Due Tomorrow ⏰",
                                "'${habit.title}' is due tomorrow ($formattedDue)"
                            )
                            daysUntil == 0L -> Triple(
                                "task_due_0_${habit.id}_$todayStr",
                                "Task Due Today 📌",
                                "'${habit.title}' is due today!"
                            )
                            daysUntil < 0L -> {
                                val daysAgo = -daysUntil
                                val dayText = if (daysAgo == 1L) "1 day" else "$daysAgo days"
                                Triple(
                                    "task_overdue_${habit.id}_$todayStr",
                                    "Task Overdue ⚠️",
                                    "'${habit.title}' was due on $formattedDue ($dayText ago)"
                                )
                            }
                            else -> null
                        }

                        if (triple != null) {
                            val (dedupKey, title, message) = triple
                            if (dedupKey !in sentKeys) {
                                notificationHelper.showNotification(title, message)
                                sentKeys.add(dedupKey)
                                updated = true
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        if (updated) {
            prefs.edit().putStringSet("sent_keys", sentKeys).apply()
        }
    }
}
