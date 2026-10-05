package com.accord.discipline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import javax.inject.Inject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class MonthlyCheckInWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MonthlyCheckInWorkerEntryPoint {
        fun notificationHelper(): NotificationHelper
    }

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        // Check if it's the first 7 days of the month
        if (today.dayOfMonth in 1..7) {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                MonthlyCheckInWorkerEntryPoint::class.java
            )
            val notificationHelper = entryPoint.notificationHelper()
            
            notificationHelper.showNotification(
                title = "Monthly Check-in Available",
                message = "Take a moment to reflect on your progress for ${today.month.name}."
            )
        }
        return Result.success()
    }
}
