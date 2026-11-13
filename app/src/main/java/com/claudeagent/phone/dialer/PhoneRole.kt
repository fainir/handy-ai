package com.claudeagent.phone.dialer

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun Context.hasPhonePermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.isDefaultPhone(): Boolean =
    getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

internal fun Context.hasCallAlerts(): Boolean {
    val notifications = getSystemService(NotificationManager::class.java)
    return notifications.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || hasPhonePermission(Manifest.permission.POST_NOTIFICATIONS)) &&
        (notifications.getNotificationChannel(CallNotifications.INCOMING_CHANNEL)?.importance
            != NotificationManager.IMPORTANCE_NONE)
}
