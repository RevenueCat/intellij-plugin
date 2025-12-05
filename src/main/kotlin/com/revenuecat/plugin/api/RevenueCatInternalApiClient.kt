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

import com.revenuecat.plugin.services.RevenueCatOAuthService
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for RevenueCat internal APIs (requires OAuth authentication)
 */
class RevenueCatInternalApiClient {

  private val baseUrl = "https://app.revenuecat.com/internal/v1"

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
   * Get offering details including paywall ID
   * @param projectId The RevenueCat project ID
   * @param offeringId The offering ID (e.g., "ofrng2d2d7bc0c2")
   * @return Result with offering details or error
   */
  fun getOfferingDetails(projectId: String, offeringId: String): Result<InternalOfferingResponse> {
    val url = "$baseUrl/developers/me/projects/$projectId/offerings/$offeringId"

    val settings = RevenueCatSettingsState.getInstance()

    // Check if we have a valid token
    if (!settings.hasValidOAuthToken()) {
      // Try to refresh if possible
      if (settings.canRefreshOAuthToken()) {
        val refreshResult = refreshTokenIfNeeded()
        if (refreshResult.isFailure) {
          return Result.failure(
            refreshResult.exceptionOrNull() ?: Exception("Token refresh failed"),
          )
        }
      } else {
        return Result.failure(
          Exception(
            "OAuth authentication required.\n\n" +
              "Please configure OAuth in Settings → Tools → RevenueCat and authorize the plugin.",
          ),
        )
      }
    }

    return executeRequest(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<InternalOfferingResponse>(body)
    }
  }

  /**
   * List all paywalls for a project (optimized endpoint)
   * Returns offering_id → paywall_id mapping
   * @param projectId The RevenueCat project ID
   * @return Result with list of paywalls or error
   */
  fun listPaywalls(projectId: String): Result<List<InternalPaywallListItem>> {
    val url = "$baseUrl/developers/me/projects/$projectId/paywalls"

    val settings = RevenueCatSettingsState.getInstance()

    // Check if we have a valid token
    if (!settings.hasValidOAuthToken()) {
      // Try to refresh if possible
      if (settings.canRefreshOAuthToken()) {
        val refreshResult = refreshTokenIfNeeded()
        if (refreshResult.isFailure) {
          return Result.failure(
            refreshResult.exceptionOrNull() ?: Exception("Token refresh failed"),
          )
        }
      } else {
        return Result.failure(
          Exception(
            "OAuth authentication required.\n\n" +
              "Please configure OAuth in Settings → Tools → RevenueCat and authorize the plugin.",
          ),
        )
      }
    }

    return executeRequest(url) { response ->
      val body = response.body?.string() ?: ""
      json.decodeFromString<List<InternalPaywallListItem>>(body)
    }
  }

  /**
   * Refresh OAuth token if needed and not already valid
   */
  private fun refreshTokenIfNeeded(): Result<Unit> {
    val settings = RevenueCatSettingsState.getInstance()

    if (settings.hasValidOAuthToken()) {
      return Result.success(Unit)
    }

    if (!settings.canRefreshOAuthToken()) {
      return Result.failure(Exception("No refresh token available"))
    }

    val oauthService = RevenueCatOAuthService.getInstance()
    return oauthService.refreshAccessToken()
      .map { tokenResponse ->
        oauthService.saveTokens(tokenResponse)
      }
  }

  /**
   * Execute internal API request with OAuth authentication
   */
  private fun <T> executeRequest(url: String, parser: (Response) -> T): Result<T> {
    val settings = RevenueCatSettingsState.getInstance()

    val request = Request.Builder()
      .url(url)
      .header("Authorization", "Bearer ${settings.oauthAccessToken}")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .header("X-Requested-With", "XMLHttpRequest")
      .get()
      .build()

    return try {
      client.newCall(request).execute().use { response ->
        when {
          response.isSuccessful -> {
            val data = parser(response)
            Result.success(data)
          }
          response.code == 401 -> {
            Result.failure(
              Exception(
                "OAuth token expired or invalid. Please re-authorize in settings.",
              ),
            )
          }
          response.code == 404 -> {
            Result.failure(Exception("Offering not found"))
          }
          else -> {
            val errorBody = response.body?.string()
            Result.failure(Exception("API error: HTTP ${response.code} - $errorBody"))
          }
        }
      }
    } catch (e: IOException) {
      Result.failure(Exception("Network error: ${e.message}"))
    } catch (e: Exception) {
      Result.failure(Exception("Unexpected error: ${e.message}"))
    }
  }
}

/**
 * Internal API offering response with paywall details
 */
@Serializable
data class InternalOfferingResponse(
  @SerialName("id") val id: String,
  @SerialName("identifier") val identifier: String,
  @SerialName("display_name") val displayName: String,
  @SerialName("is_current") val isCurrent: Boolean,
  @SerialName("created_at") val createdAt: String,
  @SerialName("metadata") val metadata: JsonElement? = null,
  @SerialName("paywall") val paywall: InternalPaywallDetails? = null,
  @SerialName("packages") val packages: List<JsonElement>? = null,
)

/**
 * Paywall details from internal API
 */
@Serializable
data class InternalPaywallDetails(
  // id: This is the paywall ID we need (e.g., "pw8426cd6a86374b3f")
  @SerialName("id") val id: String,
  @SerialName("name") val name: String? = null,
  @SerialName("template_name") val templateName: String? = null,
  @SerialName("revision") val revision: Int? = null,
  @SerialName("published_at") val publishedAt: String? = null,
  @SerialName("published_revision") val publishedRevision: Int? = null,
)

/**
 * Paywall list item from list paywalls endpoint
 * More efficient way to get paywall ID → offering ID mapping
 */
@Serializable
data class InternalPaywallListItem(
  // paywallId: e.g., "pwf4f217e41a5845ef"
  @SerialName("rc_public_id") val paywallId: String,
  // offeringId: e.g., "ofrng9dfab5d93c"
  @SerialName("offering_id") val offeringId: String,
  @SerialName("name") val name: String? = null,
  @SerialName("template_name") val templateName: String? = null,
  @SerialName("revision") val revision: Int? = null,
  @SerialName("published_revision") val publishedRevision: Int? = null,
  @SerialName("created_at") val createdAt: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null,
)
