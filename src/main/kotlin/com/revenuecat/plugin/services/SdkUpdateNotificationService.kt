/*
 * Copyright (c) 2025 RevenueCat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.revenuecat.plugin.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import com.revenuecat.plugin.wizard.ReleaseNotesDialog

/**
 * Service that checks for SDK updates on IDE startup and shows notifications
 */
class SdkUpdateNotificationService : ProjectActivity {

  override suspend fun execute(project: Project) {
    val settings = RevenueCatSettingsState.getInstance()

    // Only check if at least one notification is enabled
    if (!settings.notifyAndroidSdkUpdates &&
      !settings.notifyKmpSdkUpdates &&
      !settings.notifyFlutterSdkUpdates &&
      !settings.notifyIosSdkUpdates &&
      !settings.notifyReactNativeSdkUpdates
    ) {
      return
    }

    // Fetch latest versions in background
    checkForUpdates(project, settings)
  }

  private fun checkForUpdates(project: Project, settings: RevenueCatSettingsState) {
    Thread {
      try {
        val updates = mutableListOf<SdkUpdate>()

        // Check Android SDK
        if (settings.notifyAndroidSdkUpdates) {
          val latestVersion = GitHubVersionService.getLatestAndroidVersion()
          if (settings.lastKnownAndroidVersion.isNotEmpty() &&
            latestVersion != settings.lastKnownAndroidVersion &&
            isNewerVersion(latestVersion, settings.lastKnownAndroidVersion)
          ) {
            updates.add(SdkUpdate("Android", settings.lastKnownAndroidVersion, latestVersion))
          }
        }

        // Check iOS SDK
        if (settings.notifyIosSdkUpdates) {
          val latestVersion = GitHubVersionService.getLatestIosVersion()
          if (settings.lastKnownIosVersion.isNotEmpty() &&
            latestVersion != settings.lastKnownIosVersion &&
            isNewerVersion(latestVersion, settings.lastKnownIosVersion)
          ) {
            updates.add(SdkUpdate("iOS", settings.lastKnownIosVersion, latestVersion))
          }
        }

        // Check KMP SDK
        if (settings.notifyKmpSdkUpdates) {
          val latestVersion = GitHubVersionService.getLatestKmpVersion()
          if (settings.lastKnownKmpVersion.isNotEmpty() &&
            latestVersion != settings.lastKnownKmpVersion &&
            isNewerVersion(latestVersion, settings.lastKnownKmpVersion)
          ) {
            updates.add(
              SdkUpdate("Kotlin Multiplatform", settings.lastKnownKmpVersion, latestVersion),
            )
          }
        }

        // Check Flutter SDK
        if (settings.notifyFlutterSdkUpdates) {
          val latestVersion = GitHubVersionService.getLatestFlutterVersion()
          if (settings.lastKnownFlutterVersion.isNotEmpty() &&
            latestVersion != settings.lastKnownFlutterVersion &&
            isNewerVersion(latestVersion, settings.lastKnownFlutterVersion)
          ) {
            updates.add(SdkUpdate("Flutter", settings.lastKnownFlutterVersion, latestVersion))
          }
        }

        // Check React Native SDK
        if (settings.notifyReactNativeSdkUpdates) {
          val latestVersion = GitHubVersionService.getLatestReactNativeVersion()
          if (settings.lastKnownReactNativeVersion.isNotEmpty() &&
            latestVersion != settings.lastKnownReactNativeVersion &&
            isNewerVersion(latestVersion, settings.lastKnownReactNativeVersion)
          ) {
            updates.add(
              SdkUpdate("React Native", settings.lastKnownReactNativeVersion, latestVersion),
            )
          }
        }

        // Show notification if there are updates
        if (updates.isNotEmpty()) {
          showUpdateNotification(project, updates)
        }
      } catch (e: Exception) {
        // Silently fail - don't bother user with errors for background checks
      }
    }.start()
  }

  private fun isNewerVersion(latest: String, current: String): Boolean {
    // Simple version comparison - handles formats like "1.2.3" and "1.2.3+4.5.6"
    try {
      val latestParts = latest.split("+")[0].split(".").map { it.toIntOrNull() ?: 0 }
      val currentParts = current.split("+")[0].split(".").map { it.toIntOrNull() ?: 0 }

      for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
        val latestPart = latestParts.getOrElse(i) { 0 }
        val currentPart = currentParts.getOrElse(i) { 0 }
        if (latestPart > currentPart) return true
        if (latestPart < currentPart) return false
      }
      return false
    } catch (e: Exception) {
      // If parsing fails, just do string comparison
      return latest != current
    }
  }

  private fun showUpdateNotification(project: Project, updates: List<SdkUpdate>) {
    val title = if (updates.size == 1) {
      "RevenueCat SDK Update Available"
    } else {
      "RevenueCat SDK Updates Available"
    }

    val content = updates.joinToString("<br>") { update ->
      "<b>${update.platform}</b>: ${update.oldVersion} → ${update.newVersion}"
    }

    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("RevenueCat Notifications")
      .createNotification(title, content, NotificationType.INFORMATION)
      .addAction(object : com.intellij.notification.NotificationAction("View Release Notes") {
        override fun actionPerformed(
          e: com.intellij.openapi.actionSystem.AnActionEvent,
          notification: com.intellij.notification.Notification,
        ) {
          ReleaseNotesDialog(project).show()
          notification.expire()
        }
      })

    notification.notify(project)
  }

  private data class SdkUpdate(
    val platform: String,
    val oldVersion: String,
    val newVersion: String,
  )
}
