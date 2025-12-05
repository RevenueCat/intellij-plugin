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

import com.intellij.openapi.application.ApplicationManager
import com.revenuecat.plugin.api.RevenueCatApiClient
import com.revenuecat.plugin.api.models.RevenueSummary
import com.revenuecat.plugin.api.models.Subscription
import com.revenuecat.plugin.settings.RevenueCatSettingsState

/**
 * Application-level service for RevenueCat API operations
 */
class RevenueCatApiService {

  private var client: RevenueCatApiClient? = null
  private var cachedApiKey: String = ""
  private var cachedSdkApiKey: String = ""
  private var cachedSummary: RevenueSummary? = null

  /**
   * Get the API client, recreating if settings have changed
   */
  private fun getClient(): RevenueCatApiClient? {
    val settings = RevenueCatSettingsState.getInstance()

    if (!settings.isConfigured()) {
      return null
    }

    // Recreate client if API key or SDK API key has changed
    if (client == null || cachedApiKey != settings.apiKey || cachedSdkApiKey != settings.sdkApiKey) {
      client = RevenueCatApiClient(
        apiKey = settings.apiKey,
        sdkApiKey = settings.sdkApiKey.takeIf { it.isNotBlank() },
      )
      cachedApiKey = settings.apiKey
      cachedSdkApiKey = settings.sdkApiKey
    }

    return client
  }

  /**
   * Force reinitialize the client (call this when settings change)
   */
  fun reinitializeClient() {
    client = null
    cachedApiKey = ""
    cachedSdkApiKey = ""
    cachedSummary = null
  }

  /**
   * Fetch customer subscriptions
   */
  fun fetchSubscriptions(appUserId: String? = null): Result<List<Subscription>> {
    val apiClient = getClient() ?: return Result.failure(
      Exception("API not configured. Please set up your API key and Project ID in Settings."),
    )

    val settings = RevenueCatSettingsState.getInstance()
    val userId = appUserId ?: settings.defaultAppUserId

    if (userId.isBlank()) {
      return Result.failure(
        Exception(
          "No App User ID configured.\n\n" +
            "Please add a valid customer ID (App User ID) in Settings → Tools → RevenueCat.\n" +
            "You can find customer IDs in your RevenueCat dashboard under 'Customers'.",
        ),
      )
    }

    return apiClient.getCustomerSubscriptions(settings.projectId, userId)
      .map { it.getSubscriptionsSafe() }
  }

  /**
   * Fetch revenue summary from overview metrics API
   */
  fun calculateRevenueSummary(): Result<RevenueSummary> {
    val apiClient = getClient() ?: return Result.failure(
      Exception("API not configured. Please set up your API key and Project ID in Settings."),
    )

    val settings = RevenueCatSettingsState.getInstance()

    return apiClient.getOverviewMetrics(settings.projectId).map { metricsResponse ->
      val mrr = metricsResponse.getMRR() ?: 0.0
      val activeSubs = metricsResponse.getActiveSubscriptions()?.toInt() ?: 0
      val activeTrials = metricsResponse.getActiveTrials()?.toInt() ?: 0
      val activeUsers = metricsResponse.getActiveUsers()?.toInt() ?: 0
      val newCustomers = metricsResponse.getNewCustomers()?.toInt() ?: 0
      val revenue = metricsResponse.getRevenue() ?: 0.0

      // Try to detect currency from metrics
      val currency = metricsResponse.getMetricById("mrr")?.unit?.replace("$", "")?.trim()
        ?.takeIf { it.length == 3 } ?: "USD"

      RevenueSummary(
        mrr = mrr,
        activeSubscriptions = activeSubs,
        activeTrials = activeTrials,
        activeUsers = activeUsers,
        newCustomers = newCustomers,
        revenue = revenue,
        currency = currency,
      ).also { cachedSummary = it }
    }
  }

  /**
   * Fetch recent purchases (requires App User ID)
   */
  fun fetchRecentPurchases(
    appUserId: String? = null,
    limit: Int = 10,
  ): Result<List<com.revenuecat.plugin.api.models.Purchase>> {
    val apiClient = getClient() ?: return Result.failure(
      Exception("API not configured. Please set up your API key and Project ID in Settings."),
    )

    val settings = RevenueCatSettingsState.getInstance()
    val userId = appUserId ?: settings.defaultAppUserId

    if (userId.isBlank()) {
      return Result.failure(
        Exception(
          "App User ID required to fetch purchases.\n\n" +
            "Please provide a customer ID in Settings → Tools → RevenueCat.",
        ),
      )
    }

    return apiClient.getCustomerPurchases(settings.projectId, userId, limit)
      .map { it.getPurchasesSafe() }
  }

  /**
   * Get cached summary
   */
  fun getCachedSummary(): RevenueSummary? = cachedSummary

  /**
   * Clear cached data (but keep the client)
   */
  fun clearCache() {
    cachedSummary = null
  }

  /**
   * Get project name by project ID
   */
  fun getProjectName(projectId: String): Result<String> {
    val apiClient = getClient() ?: return Result.failure(
      Exception("API not configured. Please set up your API key in Settings."),
    )

    return apiClient.getProjects().map { projectsResponse ->
      val project = projectsResponse.getProjectsSafe().find { it.id == projectId }
      project?.name ?: projectId
    }
  }

  /**
   * Fetch offerings with packages
   */
  fun fetchOfferings(): Result<List<com.revenuecat.plugin.api.models.Offering>> {
    val apiClient = getClient() ?: return Result.failure(
      Exception("API not configured. Please set up your API key and Project ID in Settings."),
    )

    val settings = RevenueCatSettingsState.getInstance()

    return apiClient.getOfferings(settings.projectId).map { offeringsResponse ->
      offeringsResponse.getOfferingsSafe()
    }
  }

  /**
   * Fetch offerings with paywall data (v1 API)
   * Tries /v1/subscribers/{userId}/offerings first with a random user ID, then falls back to /v1/offerings
   */
  fun fetchSubscriberOfferings(
    appUserId: String? = null,
  ): Result<List<com.revenuecat.plugin.api.models.SubscriberOffering>> {
    val apiClient = getClient() ?: return Result.failure(
      Exception("API not configured. Please set up your API key in Settings."),
    )

    val settings = RevenueCatSettingsState.getInstance()

    // Try with user ID first (either provided, configured, or random)
    val userId = appUserId ?: settings.defaultAppUserId.takeIf { it.isNotBlank() }
      ?: "\$RCAnonymousID:${java.util.UUID.randomUUID()}"

    // Try subscriber offerings endpoint first
    val subscriberResult = apiClient.getSubscriberOfferings(userId)

    // If successful, return the result
    if (subscriberResult.isSuccess) {
      return subscriberResult.map { it.getOfferingsSafe() }
    }

    // Fallback to /v1/offerings endpoint
    return apiClient.getV1Offerings().map { it.getOfferingsSafe() }
  }

  /**
   * Create a new paywall for an offering
   */
  fun createPaywall(
    offeringId: String,
    name: String? = null,
  ): Result<com.revenuecat.plugin.api.models.CreatePaywallResponse> {
    val apiClient = getClient() ?: return Result.failure(
      Exception("API not configured. Please set up your API key and Project ID in Settings."),
    )

    val settings = RevenueCatSettingsState.getInstance()

    return apiClient.createPaywall(settings.projectId, offeringId, name)
  }

  companion object {
    fun getInstance(): RevenueCatApiService {
      return ApplicationManager.getApplication().getService(RevenueCatApiService::class.java)
    }
  }
}
