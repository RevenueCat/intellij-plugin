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
package com.revenuecat.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent state for RevenueCat plugin settings
 */
@State(
  name = "com.revenuecat.plugin.settings.RevenueCatSettingsState",
  storages = [Storage("RevenueCatSettings.xml")],
)
class RevenueCatSettingsState : PersistentStateComponent<RevenueCatSettingsState> {

  var apiKey: String = ""
  var sdkApiKey: String = "" // For paywall features (goog_, appl_, amzn_, stripe_, etc.)
  var projectId: String = ""
  var pollingIntervalMinutes: Int = 5
  var enableNotifications: Boolean = true
  var defaultAppUserId: String = ""

  // Webhook settings
  var enableWebhook: Boolean = false
  var webhookPort: Int = 48889
  var ngrokPath: String = "" // Path to ngrok executable (optional)
  var autoStartWebhook: Boolean = false // Auto-start webhook server on IDE startup
  var autoStartNgrok: Boolean = false // Auto-start ngrok on IDE startup

  // OAuth 2.0 settings for accessing internal APIs
  // Public OAuth client for IntelliJ Plugin (hardcoded as per OAuth2 spec for public clients)
  // oauthClientId: base64("RevenueCat IntelliJ Plugin")
  var oauthClientId: String = "UmV2ZW51ZUNhdCBJbnRlbGxpSiBQbHVnaW4="

  // oauthClientSecret: Not used for public clients
  var oauthClientSecret: String = "placeholder_not_used_for_public_client"
  var oauthAccessToken: String = "" // atk_ prefix
  var oauthRefreshToken: String = "" // rtk_ prefix
  var oauthTokenExpiresAt: Long = 0 // Unix timestamp in milliseconds

  // Paywall cache: offering_id → paywall_id mapping (JSON string)
  var paywallCache: String = "{}"

  // Milestone settings
  var mrrMilestone: Double = 0.0
  var mrrMilestoneEnabled: Boolean = false
  var activeSubscriptionsMilestone: Int = 0
  var activeSubscriptionsMilestoneEnabled: Boolean = false
  var activeTrialsMilestone: Int = 0
  var activeTrialsMilestoneEnabled: Boolean = false
  var activeUsersMilestone: Int = 0
  var activeUsersMilestoneEnabled: Boolean = false
  var newCustomersMilestone: Int = 0
  var newCustomersMilestoneEnabled: Boolean = false
  var revenueMilestone: Double = 0.0
  var revenueMilestoneEnabled: Boolean = false

  // Current values (updated from API)
  var currentMrr: Double = 0.0
  var currentActiveSubscriptions: Int = 0
  var currentActiveTrials: Int = 0
  var currentActiveUsers: Int = 0
  var currentNewCustomers: Int = 0
  var currentRevenue: Double = 0.0

  var milestoneNotificationsEnabled: Boolean = true // Global notification toggle

  // SDK Update Notification settings (all false by default)
  var notifyAndroidSdkUpdates: Boolean = false
  var notifyKmpSdkUpdates: Boolean = false
  var notifyFlutterSdkUpdates: Boolean = false
  var notifyIosSdkUpdates: Boolean = false
  var notifyReactNativeSdkUpdates: Boolean = false

  // Last known SDK versions (to detect new releases)
  var lastKnownAndroidVersion: String = ""
  var lastKnownKmpVersion: String = ""
  var lastKnownFlutterVersion: String = ""
  var lastKnownIosVersion: String = ""
  var lastKnownReactNativeVersion: String = ""

  // Blog article settings
  var notifyBlogArticles: Boolean = false // Notify when new articles are published (default false)
  var cachedBlogArticles: String = "[]" // JSON array of cached articles
  var lastKnownBlogArticleUrl: String = "" // URL of most recent article to detect new ones

  // Onboarding settings
  var hasCompletedOnboarding: Boolean = false // Whether user has seen the onboarding tooltips

  // AI Agent settings
  var aiApiKey: String = "" // API key for AI provider (OpenAI or Anthropic)
  var aiProvider: String = "OPENAI" // OPENAI or ANTHROPIC
  var aiModel: String = "GPT_4O_MINI" // Default model
  var aiEnabled: Boolean = false // Whether AI assistant is enabled

  override fun getState(): RevenueCatSettingsState = this

  override fun loadState(state: RevenueCatSettingsState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): RevenueCatSettingsState {
      return ApplicationManager.getApplication().getService(RevenueCatSettingsState::class.java)
    }
  }

  /**
   * Check if settings are configured
   */
  fun isConfigured(): Boolean {
    return apiKey.isNotBlank() && projectId.isNotBlank()
  }

  /**
   * Check if OAuth is configured
   */
  fun isOAuthConfigured(): Boolean {
    return oauthClientId.isNotBlank() && oauthClientSecret.isNotBlank()
  }

  /**
   * Check if we have a valid OAuth access token
   */
  fun hasValidOAuthToken(): Boolean {
    if (oauthAccessToken.isBlank()) return false
    // Check if token is expired (with 5 minute buffer)
    val now = System.currentTimeMillis()
    return oauthTokenExpiresAt > now + (5 * 60 * 1000)
  }

  /**
   * Check if we can refresh the OAuth token
   */
  fun canRefreshOAuthToken(): Boolean {
    return isOAuthConfigured() && oauthRefreshToken.isNotBlank()
  }

  /**
   * Get cached paywall ID for an offering
   */
  fun getCachedPaywallId(offeringId: String): String? {
    return try {
      val cache = kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(
        paywallCache,
      )
      cache[offeringId]
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Cache paywall ID for an offering
   */
  fun cachePaywallId(offeringId: String, paywallId: String) {
    try {
      val cache = kotlinx.serialization.json.Json.decodeFromString<MutableMap<String, String>>(
        paywallCache,
      )
      cache[offeringId] = paywallId
      paywallCache = kotlinx.serialization.json.Json.encodeToString(
        kotlinx.serialization.serializer<Map<String, String>>(),
        cache,
      )
    } catch (e: Exception) {
      // If cache is invalid, create new cache
      val newCache = mapOf(offeringId to paywallId)
      paywallCache = kotlinx.serialization.json.Json.encodeToString(
        kotlinx.serialization.serializer<Map<String, String>>(),
        newCache,
      )
    }
  }

  /**
   * Check if AI assistant is configured
   */
  fun isAIConfigured(): Boolean {
    return aiEnabled && aiApiKey.isNotBlank()
  }
}
