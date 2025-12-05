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
package com.revenuecat.plugin.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.revenuecat.plugin.services.PurchaseNotificationService
import com.revenuecat.plugin.services.RevenueCatWebhookServer
import com.revenuecat.plugin.settings.RevenueCatSettingsState

/**
 * Startup activity to initialize the purchase notification service and webhook server
 */
class ProjectStartupActivity : StartupActivity {

  override fun runActivity(project: Project) {
    val settings = RevenueCatSettingsState.getInstance()

    // Start polling for new purchases when project opens (legacy)
    val notificationService = PurchaseNotificationService.getInstance(project)
    notificationService.startPolling()

    // Auto-start webhook server if configured
    if (settings.enableNotifications && settings.enableWebhook && settings.autoStartWebhook) {
      val webhookServer = RevenueCatWebhookServer.getInstance()
      if (!webhookServer.isRunning()) {
        webhookServer.start(settings.webhookPort)
      }
    }

    // Auto-start ngrok if configured
    if (settings.enableNotifications && settings.enableWebhook && settings.autoStartNgrok && settings.ngrokPath.isNotBlank()) {
      startNgrok(settings)
    }
  }

  private fun startNgrok(settings: RevenueCatSettingsState) {
    Thread {
      try {
        Thread.sleep(2000) // Wait a bit for webhook server to start

        val processBuilder =
          ProcessBuilder(settings.ngrokPath, "http", settings.webhookPort.toString())
        processBuilder.start()

        println("RevenueCat: ngrok started automatically on port ${settings.webhookPort}")
      } catch (e: Exception) {
        println("RevenueCat: Failed to auto-start ngrok: ${e.message}")
      }
    }.start()
  }
}
