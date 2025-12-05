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
package com.revenuecat.plugin.api

import com.revenuecat.plugin.api.models.ApiError
import com.revenuecat.plugin.api.models.CreatePaywallResponse
import com.revenuecat.plugin.api.models.OfferingsListResponse
import com.revenuecat.plugin.api.models.OverviewMetricsResponse
import com.revenuecat.plugin.api.models.ProjectsListResponse
import com.revenuecat.plugin.api.models.PurchaseResponse
import com.revenuecat.plugin.api.models.PurchasesListResponse
import com.revenuecat.plugin.api.models.SubscriberOfferingsResponse
import com.revenuecat.plugin.api.models.SubscriptionsResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * RevenueCat API v2 Client
 * Handles all API communication with RevenueCat REST API
 */
class RevenueCatApiClient(
  private val apiKey: String,
  private val sdkApiKey: String? = null,
) {

  private val baseUrl = "https://api.revenuecat.com/v2"
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

  /**
   * Get customer subscriptions
   * @param projectId The RevenueCat project ID
   * @param appUserId The app user ID
   * @return SubscriptionsResponse containing subscription data
   */
  fun getCustomerSubscriptions(
    projectId: String,
    appUserId: String,
  ): Result<SubscriptionsResponse> {
    val url = "$baseUrl/projects/$projectId/customers/$appUserId/subscriptions"
    return executeRequest(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<SubscriptionsResponse>(body)
    }
  }

  /**
   * Get overview metrics (MRR, ARR, active subscriptions, etc.)
   * @param projectId The RevenueCat project ID
   * @param currency Optional currency code (e.g., "USD")
   * @return OverviewMetricsResponse containing all metrics
   */
  fun getOverviewMetrics(
    projectId: String,
    currency: String? = null,
  ): Result<OverviewMetricsResponse> {
    val urlBuilder = StringBuilder("$baseUrl/projects/$projectId/metrics/overview")
    if (currency != null) {
      urlBuilder.append("?currency=$currency")
    }
    val url = urlBuilder.toString()

    return executeRequest(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<OverviewMetricsResponse>(body)
    }
  }

  /**
   * Get list of purchases for a customer
   * @param projectId The RevenueCat project ID
   * @param appUserId The app user ID
   * @param limit Optional limit on number of purchases to return
   * @return PurchasesListResponse containing purchase list
   */
  fun getCustomerPurchases(
    projectId: String,
    appUserId: String,
    limit: Int = 20,
  ): Result<PurchasesListResponse> {
    val url = "$baseUrl/projects/$projectId/customers/$appUserId/purchases?limit=$limit"
    return executeRequest(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<PurchasesListResponse>(body)
    }
  }

  /**
   * Get a specific purchase by ID
   * @param projectId The RevenueCat project ID
   * @param purchaseId The purchase ID
   * @return PurchaseResponse containing purchase data
   */
  fun getPurchase(projectId: String, purchaseId: String): Result<PurchaseResponse> {
    val url = "$baseUrl/projects/$projectId/purchases/$purchaseId"
    return executeRequest(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<PurchaseResponse>(body)
    }
  }

  /**
   * Get list of projects
   * @return ProjectsListResponse containing all projects
   */
  fun getProjects(): Result<ProjectsListResponse> {
    val url = "$baseUrl/projects"
    return executeRequest(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<ProjectsListResponse>(body)
    }
  }

  /**
   * Get list of offerings with packages
   * @param projectId The RevenueCat project ID
   * @return OfferingsListResponse containing all offerings with their packages
   */
  fun getOfferings(projectId: String): Result<OfferingsListResponse> {
    val url = "$baseUrl/projects/$projectId/offerings?expand=items.package"
    return executeRequest(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<OfferingsListResponse>(body)
    }
  }

  /**
   * Get subscriber-specific offerings with paywall data (v1 API)
   * @param appUserId The app user ID
   * @return SubscriberOfferingsResponse containing offerings with paywall data
   */
  fun getSubscriberOfferings(appUserId: String): Result<SubscriberOfferingsResponse> {
    val url = "https://api.revenuecat.com/v1/subscribers/$appUserId/offerings"
    return executeV1Request(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<SubscriberOfferingsResponse>(body)
    }
  }

  /**
   * Get offerings with paywall data (v1 API) - fallback endpoint that doesn't require user ID
   * @return SubscriberOfferingsResponse containing offerings with paywall data
   */
  fun getV1Offerings(): Result<SubscriberOfferingsResponse> {
    val url = "https://api.revenuecat.com/v1/offerings"
    return executeV1Request(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<SubscriberOfferingsResponse>(body)
    }
  }

  /**
   * Execute V1 API request with required platform headers
   * Uses SDK API key ONLY (required for v1 paywall endpoints)
   */
  private fun <T> executeV1Request(url: String, parser: (Response) -> T): Result<T> {
    // SDK API key is required for v1 API
    if (sdkApiKey.isNullOrBlank()) {
      return Result.failure(
        Exception(
          "SDK API Key is required for paywall features.\n\n" +
            "Please add your SDK API Key (goog_, appl_, amzn_, or stripe_) in Settings → Tools → RevenueCat.",
        ),
      )
    }

    val request = Request.Builder()
      .url(url)
      .header("Authorization", "Bearer $sdkApiKey")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("X-Platform", "android")
      .header("X-Platform-Version", System.getProperty("java.version") ?: "unknown")
      .header("X-Client-Locale", java.util.Locale.getDefault().toString())
      .header(
        "X-Preferred-Locales",
        java.util.Locale.getDefault().toLanguageTag().replace('-', '_'),
      )
      .get()
      .build()

    return executeRequestInternal(request, parser)
  }

  /**
   * Generic request executor with error handling
   * @param url The API endpoint URL
   * @param parser Function to parse the response
   * @return Result containing either the parsed data or an error
   */
  private fun <T> executeRequest(url: String, parser: (Response) -> T): Result<T> {
    val request = Request.Builder()
      .url(url)
      .header("Authorization", "Bearer $apiKey")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .get()
      .build()

    return executeRequestInternal(request, parser)
  }

  /**
   * Internal request executor
   */
  private fun <T> executeRequestInternal(request: Request, parser: (Response) -> T): Result<T> {
    return try {
      client.newCall(request).execute().use { response ->
        when {
          response.isSuccessful -> {
            val data = parser(response)
            Result.success(data)
          }
          response.code == 401 -> {
            Result.failure(ApiException("Invalid API key or unauthorized", 401))
          }
          response.code == 404 -> {
            val errorBody = response.body?.string()
            val errorMessage = parseErrorMessage(errorBody) ?: "Resource not found"
            val detailedMessage = buildString {
              append(errorMessage)
              append("\n\n")
              append("This usually means:\n")
              append(
                "• The App User ID (customer ID) doesn't exist in your project\n",
              )
              append("• The customer has no subscriptions or purchases yet\n")
              append("• The Project ID might be incorrect\n\n")
              append("Please verify:\n")
              append(
                "1. Your App User ID is correct (check RevenueCat Dashboard → Customers)\n",
              )
              append("2. The customer has at least one purchase/subscription\n")
              append(
                "3. Your Project ID matches the project containing this customer",
              )
            }
            Result.failure(ApiException(detailedMessage, 404))
          }
          response.code == 429 -> {
            Result.failure(ApiException("Rate limit exceeded", 429))
          }
          else -> {
            val errorBody = response.body?.string()
            val errorMessage = try {
              json.decodeFromString<ApiError>(errorBody ?: "").message
            } catch (e: Exception) {
              "API error: ${response.code}"
            }
            Result.failure(ApiException(errorMessage, response.code))
          }
        }
      }
    } catch (e: IOException) {
      Result.failure(ApiException("Network error: ${e.message}", null))
    } catch (e: Exception) {
      Result.failure(ApiException("Unexpected error: ${e.message}", null))
    }
  }

  /**
   * Create a new paywall for an offering
   * @param projectId The RevenueCat project ID
   * @param offeringId The offering ID to attach the paywall to
   * @param name Optional name for the paywall
   * @return CreatePaywallResponse containing the newly created paywall data including paywall_id
   */
  fun createPaywall(
    projectId: String,
    offeringId: String,
    name: String? = null,
  ): Result<CreatePaywallResponse> {
    val url = "$baseUrl/projects/$projectId/paywalls"

    // Build request body - only offering_id is required
    val requestBody = """
            {
                "offering_id": "$offeringId"
            }
    """.trimIndent()

    val body = requestBody.toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
      .url(url)
      .post(body)
      .header("Authorization", "Bearer $apiKey")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .build()

    return try {
      client.newCall(request).execute().use { response ->
        val responseBody = response.body?.string() ?: ""

        when {
          response.isSuccessful -> {
            val data = json.decodeFromString<CreatePaywallResponse>(responseBody)
            Result.success(data)
          }
          response.code == 401 -> {
            Result.failure(ApiException("Invalid API key or unauthorized", 401))
          }
          response.code == 403 -> {
            Result.failure(
              ApiException("API key does not have permission to create paywalls", 403),
            )
          }
          else -> {
            val errorMessage = try {
              json.decodeFromString<ApiError>(responseBody).message
            } catch (e: Exception) {
              "Failed to create paywall: HTTP ${response.code}"
            }
            Result.failure(ApiException(errorMessage, response.code))
          }
        }
      }
    } catch (e: IOException) {
      Result.failure(ApiException("Network error: ${e.message}", null))
    } catch (e: Exception) {
      Result.failure(ApiException("Unexpected error: ${e.message}", null))
    }
  }

  /**
   * Test API connection by trying to fetch a test customer's subscriptions
   * @param projectId The RevenueCat project ID
   * @param testAppUserId Optional test customer ID to validate against
   * @return Result indicating success or failure
   */
  fun testConnection(projectId: String, testAppUserId: String? = null): Result<Boolean> {
    // Use a test customer ID or a dummy one
    val userId = testAppUserId ?: "\$RCAnonymousID:test-connection-id"
    val url = "$baseUrl/projects/$projectId/customers/$userId/subscriptions"

    return try {
      val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .get()
        .build()

      client.newCall(request).execute().use { response ->
        when {
          response.isSuccessful -> {
            // 2xx response means credentials are valid
            Result.success(true)
          }
          response.code == 404 -> {
            // 404 means auth worked but customer not found - credentials are valid
            Result.success(true)
          }
          response.code == 401 -> {
            val errorBody = response.body?.string()
            val errorMessage = parseErrorMessage(
              errorBody,
            ) ?: "Invalid or expired API key"
            Result.failure(ApiException(errorMessage, 401))
          }
          response.code == 403 -> {
            val errorBody = response.body?.string()
            val errorMessage = parseErrorMessage(
              errorBody,
            ) ?: "API key does not have permission to access this project"
            Result.failure(ApiException(errorMessage, 403))
          }
          else -> {
            val errorBody = response.body?.string()
            val errorMessage = parseErrorMessage(
              errorBody,
            ) ?: "Connection failed with code ${response.code}"
            Result.failure(ApiException(errorMessage, response.code))
          }
        }
      }
    } catch (e: Exception) {
      Result.failure(ApiException("Network error: ${e.message}", null))
    }
  }

  /**
   * Parse error message from RevenueCat API response
   */
  private fun parseErrorMessage(errorBody: String?): String? {
    if (errorBody.isNullOrBlank()) return null

    return try {
      val errorResponse = json.decodeFromString<ApiError>(errorBody)
      errorResponse.message
    } catch (e: Exception) {
      // If we can't parse as JSON, return the raw error body
      errorBody.take(200) // Limit length
    }
  }
}

/**
 * Custom exception for API errors
 */
class ApiException(message: String, val code: Int?) : Exception(message)
