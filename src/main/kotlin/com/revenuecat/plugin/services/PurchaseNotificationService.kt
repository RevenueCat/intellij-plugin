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
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.revenuecat.plugin.api.models.Subscription
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Service for polling RevenueCat API and notifying about new purchases
 */
@Service(Service.Level.PROJECT)
class PurchaseNotificationService(private val project: Project) : Disposable {

  private var scheduler: ScheduledExecutorService? = null
  private var lastKnownSubscriptionIds = mutableSetOf<String>()
  private var isInitialized = false

  fun startPolling() {
    val settings = RevenueCatSettingsState.getInstance()

    if (!settings.isConfigured() || !settings.enableNotifications) {
      return
    }

    stopPolling()

    val apiService = RevenueCatApiService.getInstance()

    // Initialize with current subscriptions
    if (!isInitialized) {
      val result = apiService.fetchSubscriptions()
      if (result.isSuccess) {
        lastKnownSubscriptionIds = result.getOrNull()!!.map { it.id }.toMutableSet()
        isInitialized = true
      }
    }

    scheduler = Executors.newSingleThreadScheduledExecutor().apply {
      scheduleAtFixedRate(
        { checkForNewPurchases() },
        settings.pollingIntervalMinutes.toLong(),
        settings.pollingIntervalMinutes.toLong(),
        TimeUnit.MINUTES,
      )
    }
  }

  fun stopPolling() {
    scheduler?.shutdown()
    scheduler = null
  }

  private fun checkForNewPurchases() {
    val settings = RevenueCatSettingsState.getInstance()

    if (!settings.isConfigured() || !settings.enableNotifications) {
      return
    }

    val apiService = RevenueCatApiService.getInstance()
    val result = apiService.fetchSubscriptions()

    if (result.isSuccess) {
      val currentSubscriptions = result.getOrNull()!!
      val currentIds = currentSubscriptions.map { it.id }.toSet()

      // Find new subscriptions
      val newSubscriptions = currentSubscriptions.filter { subscription ->
        subscription.id !in lastKnownSubscriptionIds
      }

      // Show notifications for new subscriptions
      newSubscriptions.forEach { subscription ->
        showNotification(subscription)
      }

      // Update known subscriptions
      lastKnownSubscriptionIds = currentIds.toMutableSet()
    }
  }

  private fun showNotification(subscription: Subscription) {
    val notificationGroup = NotificationGroupManager.getInstance()
      .getNotificationGroup("RevenueCat Notifications")

    val priceInfo = if (subscription.price != null && subscription.currency != null) {
      " - ${subscription.price} ${subscription.currency}"
    } else {
      ""
    }

    val notification = notificationGroup.createNotification(
      "New Purchase Detected!",
      "Product: ${subscription.productId}$priceInfo\nStatus: ${subscription.status}",
      NotificationType.INFORMATION,
    )

    notification.notify(project)
  }

  override fun dispose() {
    stopPolling()
  }

  companion object {
    fun getInstance(project: Project): PurchaseNotificationService {
      return project.getService(PurchaseNotificationService::class.java)
    }
  }
}
