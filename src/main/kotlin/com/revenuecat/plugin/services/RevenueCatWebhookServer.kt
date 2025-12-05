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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress

/**
 * Local HTTP server to handle RevenueCat webhook events
 */
@Service
class RevenueCatWebhookServer {
  private var server: HttpServer? = null
  private var isRunning = false
  private val json = Json { ignoreUnknownKeys = true }

  companion object {
    fun getInstance(): RevenueCatWebhookServer {
      return ApplicationManager.getApplication().getService(
        RevenueCatWebhookServer::class.java,
      )
    }
  }

  /**
   * Start the webhook server on the specified port
   */
  fun start(port: Int = 48889): Boolean {
    if (isRunning) {
      println("RevenueCat Webhook Server: Already running")
      return true
    }

    try {
      server = HttpServer.create(InetSocketAddress(port), 0)

      // Handle RevenueCat webhook events
      server?.createContext("/revenuecat/webhook") { exchange ->
        try {
          if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
            // Read the request body
            val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
            println("RevenueCat Webhook: Received event:\n$requestBody")

            // Parse the webhook event
            handleWebhookEvent(requestBody)

            // Send success response
            val response = """{"status":"ok"}"""
            val responseBytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { os ->
              os.write(responseBytes)
            }
          } else {
            // Return 405 Method Not Allowed for non-POST requests
            val response = """{"error":"Method not allowed"}"""
            val responseBytes = response.toByteArray()
            exchange.sendResponseHeaders(405, responseBytes.size.toLong())
            exchange.responseBody.use { os ->
              os.write(responseBytes)
            }
          }
        } catch (e: Exception) {
          println("RevenueCat Webhook: Error handling request: ${e.message}")
          e.printStackTrace()

          // Send error response
          val response = """{"error":"${e.message}"}"""
          val responseBytes = response.toByteArray()
          exchange.sendResponseHeaders(500, responseBytes.size.toLong())
          exchange.responseBody.use { os ->
            os.write(responseBytes)
          }
        }
      }

      // Health check endpoint
      server?.createContext("/health") { exchange ->
        val response = """{"status":"healthy"}"""
        val responseBytes = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, responseBytes.size.toLong())
        exchange.responseBody.use { os ->
          os.write(responseBytes)
        }
      }

      server?.executor = null // Use default executor
      server?.start()
      isRunning = true

      println("RevenueCat Webhook Server: Started on port $port")
      showNotification(
        "Webhook Server Started",
        "RevenueCat webhook server is now listening on port $port",
        NotificationType.INFORMATION,
      )

      return true
    } catch (e: Exception) {
      println("RevenueCat Webhook Server: Failed to start: ${e.message}")
      e.printStackTrace()
      showNotification(
        "Webhook Server Error",
        "Failed to start webhook server: ${e.message}",
        NotificationType.ERROR,
      )
      return false
    }
  }

  /**
   * Stop the webhook server
   */
  fun stop() {
    if (!isRunning) {
      return
    }

    server?.stop(0)
    server = null
    isRunning = false

    println("RevenueCat Webhook Server: Stopped")
    showNotification(
      "Webhook Server Stopped",
      "RevenueCat webhook server has been stopped",
      NotificationType.INFORMATION,
    )
  }

  /**
   * Check if the server is running
   */
  fun isRunning(): Boolean = isRunning

  /**
   * Handle incoming webhook event
   */
  private fun handleWebhookEvent(eventJson: String) {
    try {
      val eventObject = json.parseToJsonElement(eventJson).jsonObject
      val eventType = eventObject["event"]?.jsonObject?.get("type")?.jsonPrimitive?.content

      when (eventType) {
        "INITIAL_PURCHASE" -> handleInitialPurchase(eventObject)
        "RENEWAL" -> handleRenewal(eventObject)
        "CANCELLATION" -> handleCancellation(eventObject)
        "NON_RENEWING_PURCHASE" -> handleNonRenewingPurchase(eventObject)
        "PRODUCT_CHANGE" -> handleProductChange(eventObject)
        "BILLING_ISSUE" -> handleBillingIssue(eventObject)
        "SUBSCRIBER_ALIAS" -> handleSubscriberAlias(eventObject)
        "EXPIRATION" -> handleExpiration(eventObject)
        "UNCANCELLATION" -> handleUncancellation(eventObject)
        "SUBSCRIPTION_PAUSED" -> handleSubscriptionPaused(eventObject)
        "TRANSFER" -> handleTransfer(eventObject)
        "SUBSCRIPTION_EXTENDED" -> handleSubscriptionExtended(eventObject)
        "TEMPORARY_ENTITLEMENT_GRANT" -> handleTemporaryEntitlementGrant(eventObject)
        "REFUND_REVERSED" -> handleRefundReversed(eventObject)
        "INVOICE_ISSUANCE" -> handleInvoiceIssuance(eventObject)
        "VIRTUAL_CURRENCY_TRANSACTION" -> handleVirtualCurrencyTransaction(eventObject)
        "EXPERIMENT_ENROLLMENT" -> handleExperimentEnrollment(eventObject)
        "TEST" -> handleTestEvent(eventObject)
        else -> {
          println("RevenueCat Webhook: Unknown event type: $eventType")
          showNotification(
            "RevenueCat Event",
            "Received unknown event type: $eventType",
            NotificationType.INFORMATION,
          )
        }
      }
    } catch (e: Exception) {
      println("RevenueCat Webhook: Error parsing event: ${e.message}")
      e.printStackTrace()
    }
  }

  private fun handleInitialPurchase(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val priceInCurrency = event["event"]?.jsonObject?.get(
      "price_in_purchased_currency",
    )?.jsonPrimitive?.content
    val currency = event["event"]?.jsonObject?.get("currency")?.jsonPrimitive?.content ?: ""
    val store = event["event"]?.jsonObject?.get("store")?.jsonPrimitive?.content ?: "Unknown"

    val priceDisplay = if (priceInCurrency != null && currency.isNotEmpty()) {
      " for $currency $priceInCurrency"
    } else {
      ""
    }

    showNotification(
      "🎉 Congratulations! New Subscription!",
      "Customer $appUserId just subscribed to $productId$priceDisplay via $store!\n\nA new subscription has been acquired.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleRenewal(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val renewalNumber = event["event"]?.jsonObject?.get(
      "renewal_number",
    )?.jsonPrimitive?.content

    val renewalInfo = if (renewalNumber != null) " (Renewal #$renewalNumber)" else ""

    showNotification(
      "🔄 Subscription Renewed!",
      "Great news! Customer $appUserId renewed their subscription to $productId$renewalInfo\n\nAn existing subscription has renewed or a lapsed user resubscribed.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleCancellation(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val expirationMs = event["event"]?.jsonObject?.get(
      "expiration_at_ms",
    )?.jsonPrimitive?.content

    val expirationInfo = if (expirationMs != null) {
      val expirationDate = java.util.Date(expirationMs.toLong())
      "\n\nExpires: $expirationDate"
    } else {
      ""
    }

    showNotification(
      "⚠️ Subscription Cancelled",
      "Customer $appUserId cancelled $productId$expirationInfo\n\nA subscription or non-renewing purchase has been cancelled or refunded.",
      NotificationType.WARNING,
    )
  }

  private fun handleNonRenewingPurchase(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val priceInCurrency = event["event"]?.jsonObject?.get(
      "price_in_purchased_currency",
    )?.jsonPrimitive?.content
    val currency = event["event"]?.jsonObject?.get("currency")?.jsonPrimitive?.content ?: ""
    val store = event["event"]?.jsonObject?.get("store")?.jsonPrimitive?.content ?: "Unknown"

    val priceDisplay = if (priceInCurrency != null && currency.isNotEmpty()) {
      " for $currency $priceInCurrency"
    } else {
      ""
    }

    showNotification(
      "🎉 New One-Time Purchase!",
      "Customer $appUserId purchased $productId$priceDisplay via $store!\n\nA one-time purchase has been completed.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleProductChange(event: kotlinx.serialization.json.JsonObject) {
    val newProductId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "🔄 Product Changed",
      "Customer $appUserId modified their subscription to $newProductId\n\nA subscriber has changed their product tier.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleBillingIssue(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "💳 Billing Issue Detected",
      "Problem charging customer $appUserId for $productId\n\nThere has been a problem trying to charge the subscriber. This may resolve automatically or the customer may need to update their payment method.",
      NotificationType.WARNING,
    )
  }

  private fun handleSubscriberAlias(event: kotlinx.serialization.json.JsonObject) {
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val aliases = event["event"]?.jsonObject?.get("aliases")?.jsonArray

    showNotification(
      "🔗 Subscriber Alias Created",
      "User IDs linked for $appUserId\n\nTransactions and entitlements transferred between user IDs.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleExpiration(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val expirationMs = event["event"]?.jsonObject?.get(
      "expiration_at_ms",
    )?.jsonPrimitive?.content

    val expirationInfo = if (expirationMs != null) {
      val expirationDate = java.util.Date(expirationMs.toLong())
      "\n\nExpired: $expirationDate"
    } else {
      ""
    }

    showNotification(
      "⏰ Subscription Expired",
      "Customer $appUserId's subscription to $productId has expired$expirationInfo\n\nAccess should be removed.",
      NotificationType.WARNING,
    )
  }

  private fun handleUncancellation(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "✅ Subscription Re-enabled!",
      "Good news! Customer $appUserId reversed cancellation of $productId\n\nA non-expired cancelled subscription has been re-enabled.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleSubscriptionPaused(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "⏸️ Subscription Paused",
      "Customer $appUserId paused $productId\n\nThe subscription is set to be paused at the end of the period.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleTransfer(event: kotlinx.serialization.json.JsonObject) {
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "🔄 Subscription Transferred",
      "Subscription transferred for $appUserId\n\nTransactions and entitlements transferred between user IDs.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleSubscriptionExtended(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val newExpirationMs = event["event"]?.jsonObject?.get(
      "expiration_at_ms",
    )?.jsonPrimitive?.content

    val expirationInfo = if (newExpirationMs != null) {
      val expirationDate = java.util.Date(newExpirationMs.toLong())
      "\n\nNew expiration: $expirationDate"
    } else {
      ""
    }

    showNotification(
      "🎉 Subscription Extended!",
      "Great news! Customer $appUserId's subscription to $productId has been extended$expirationInfo\n\nA subscription's expiration date has been extended.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleTemporaryEntitlementGrant(event: kotlinx.serialization.json.JsonObject) {
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val entitlementIds = event["event"]?.jsonObject?.get("entitlement_ids")?.jsonArray

    showNotification(
      "🔓 Temporary Access Granted",
      "Temporary access granted to customer $appUserId\n\nTemporary entitlement granted during store validation issues.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleRefundReversed(event: kotlinx.serialization.json.JsonObject) {
    val productId = event["event"]?.jsonObject?.get(
      "product_id",
    )?.jsonPrimitive?.content ?: "Unknown"
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "🎉 Refund Reversed!",
      "Great news! A refund for customer $appUserId's purchase of $productId was reversed\n\nA previously issued refund has been reversed.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleInvoiceIssuance(event: kotlinx.serialization.json.JsonObject) {
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "📄 Invoice Issued",
      "New invoice created for customer $appUserId\n\nA new invoice has been created for Web Billing.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleVirtualCurrencyTransaction(event: kotlinx.serialization.json.JsonObject) {
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "💰 Virtual Currency Transaction",
      "Virtual currency transaction for customer $appUserId\n\nVirtual currency adjusted from purchases or refunds.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleExperimentEnrollment(event: kotlinx.serialization.json.JsonObject) {
    val appUserId = event["event"]?.jsonObject?.get(
      "app_user_id",
    )?.jsonPrimitive?.content ?: "Unknown"

    showNotification(
      "🧪 Experiment Enrollment",
      "Customer $appUserId enrolled in an experiment\n\nA customer has joined an experiment cohort.",
      NotificationType.INFORMATION,
    )
  }

  private fun handleTestEvent(event: kotlinx.serialization.json.JsonObject) {
    showNotification(
      "✅ Test Event Received",
      "RevenueCat webhook test event received successfully!\n\nTest event issued through the RevenueCat dashboard.",
      NotificationType.INFORMATION,
    )
  }

  /**
   * Show IDE notification
   */
  private fun showNotification(title: String, content: String, type: NotificationType) {
    ApplicationManager.getApplication().invokeLater {
      NotificationGroupManager.getInstance()
        .getNotificationGroup("RevenueCat Notifications")
        .createNotification(title, content, type)
        .notify(null)
    }
  }
}
