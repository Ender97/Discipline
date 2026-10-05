package com.accord.discipline

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class DisciplineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleMonthlyCheckInNudge()
        scheduleTaskDueDateCheck()
    }

    private fun scheduleMonthlyCheckInNudge() {
        val workRequest = PeriodicWorkRequestBuilder<MonthlyCheckInWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MonthlyCheckInNudge",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleTaskDueDateCheck() {
        val workRequest = PeriodicWorkRequestBuilder<TaskDueDateWorker>(12, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TaskDueDateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
