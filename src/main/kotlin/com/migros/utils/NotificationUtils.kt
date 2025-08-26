package com.migros.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

class NotificationUtils {

    companion object  {
        fun showNotification(project: Project, message : String, notificationType: NotificationType) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Custom Notifications")
                .createNotification(
                    message,
                    notificationType
                )
                .notify(project)
        }
    }

}