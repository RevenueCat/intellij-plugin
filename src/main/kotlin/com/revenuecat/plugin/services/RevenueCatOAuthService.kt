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
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Service for handling RevenueCat OAuth 2.0 authentication
 */
class RevenueCatOAuthService {

  private val authorizationEndpoint = "https://api.revenuecat.com/oauth2/authorize"
  private val tokenEndpoint = "https://api.revenuecat.com/oauth2/token"
  private val redirectUri = "http://localhost:48888/oauth/callback" // Local server for callback

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
   * Generate authorization URL for OAuth flow
   * @param scopes List of scopes to request
   * @return Authorization URL to open in browser
   */
  fun generateAuthorizationUrl(
    scopes: List<String> = listOf("project_configuration:offerings:read"),
  ): Pair<String, String> {
    val settings = RevenueCatSettingsState.getInstance()

    if (!settings.isOAuthConfigured()) {
      throw Exception("OAuth client ID and secret must be configured in settings")
    }

    // Generate state parameter for CSRF protection
    val state = generateSecureRandomString(32)

    // Generate PKCE code verifier and challenge
    val codeVerifier = generateSecureRandomString(64)
    val codeChallenge = generateCodeChallenge(codeVerifier)

    val scopeString = scopes.joinToString(" ")

    val params = mapOf(
      "client_id" to settings.oauthClientId,
      "response_type" to "code",
      "redirect_uri" to redirectUri,
      "scope" to scopeString,
      "state" to state,
      "code_challenge" to codeChallenge,
      "code_challenge_method" to "S256",
    )

    val queryString = params.entries.joinToString("&") { (key, value) ->
      "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
    }

    val authUrl = "$authorizationEndpoint?$queryString"

    // Return both the URL and the code verifier (needed for token exchange)
    return Pair(authUrl, codeVerifier)
  }

  /**
   * Exchange authorization code for access token
   * @param code Authorization code from callback
   * @param codeVerifier PKCE code verifier used in authorization request
   * @return Result with token response or error
   */
  fun exchangeCodeForToken(code: String, codeVerifier: String): Result<OAuthTokenResponse> {
    val settings = RevenueCatSettingsState.getInstance()

    val requestBody = FormBody.Builder()
      .add("grant_type", "authorization_code")
      .add("code", code)
      .add("redirect_uri", redirectUri)
      .add("client_id", settings.oauthClientId)
      .add("client_secret", settings.oauthClientSecret)
      .add("code_verifier", codeVerifier)
      .build()

    val request = Request.Builder()
      .url(tokenEndpoint)
      .post(requestBody)
      .header("Content-Type", "application/x-www-form-urlencoded")
      .header("Accept", "application/json")
      .build()

    return executeTokenRequest(request)
  }

  /**
   * Refresh access token using refresh token
   * @return Result with new token response or error
   */
  fun refreshAccessToken(): Result<OAuthTokenResponse> {
    val settings = RevenueCatSettingsState.getInstance()

    if (!settings.canRefreshOAuthToken()) {
      return Result.failure(Exception("No refresh token available or OAuth not configured"))
    }

    val requestBody = FormBody.Builder()
      .add("grant_type", "refresh_token")
      .add("refresh_token", settings.oauthRefreshToken)
      .add("client_id", settings.oauthClientId)
      .add("client_secret", settings.oauthClientSecret)
      .build()

    val request = Request.Builder()
      .url(tokenEndpoint)
      .post(requestBody)
      .header("Content-Type", "application/x-www-form-urlencoded")
      .header("Accept", "application/json")
      .build()

    return executeTokenRequest(request)
  }

  /**
   * Save OAuth tokens to settings
   */
  fun saveTokens(tokenResponse: OAuthTokenResponse) {
    val settings = RevenueCatSettingsState.getInstance()

    settings.oauthAccessToken = tokenResponse.accessToken
    settings.oauthRefreshToken = tokenResponse.refreshToken ?: settings.oauthRefreshToken

    // Calculate expiration time (current time + expires_in seconds - 5 minute buffer)
    val expiresInMs = (tokenResponse.expiresIn - 300) * 1000L // 5 minute buffer
    settings.oauthTokenExpiresAt = System.currentTimeMillis() + expiresInMs
  }

  /**
   * Execute token request and parse response
   */
  private fun executeTokenRequest(request: Request): Result<OAuthTokenResponse> {
    return try {
      client.newCall(request).execute().use { response ->
        val body = response.body?.string() ?: ""

        when {
          response.isSuccessful -> {
            val tokenResponse = json.decodeFromString<OAuthTokenResponse>(body)
            Result.success(tokenResponse)
          }
          else -> {
            val errorMessage = try {
              val error = json.decodeFromString<OAuthErrorResponse>(body)
              "${error.error}: ${error.errorDescription ?: "Unknown error"}"
            } catch (e: Exception) {
              "OAuth error: HTTP ${response.code} - $body"
            }
            Result.failure(Exception(errorMessage))
          }
        }
      }
    } catch (e: IOException) {
      Result.failure(Exception("Network error: ${e.message}"))
    } catch (e: Exception) {
      Result.failure(Exception("Unexpected error: ${e.message}"))
    }
  }

  /**
   * Generate secure random string for state/verifier
   */
  private fun generateSecureRandomString(length: Int): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    val random = SecureRandom()
    return (1..length)
      .map { chars[random.nextInt(chars.length)] }
      .joinToString("")
  }

  /**
   * Generate PKCE code challenge from verifier
   */
  private fun generateCodeChallenge(codeVerifier: String): String {
    val bytes = codeVerifier.toByteArray(StandardCharsets.US_ASCII)
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
  }

  companion object {
    fun getInstance(): RevenueCatOAuthService {
      return ApplicationManager.getApplication().getService(
        RevenueCatOAuthService::class.java,
      )
    }
  }
}

/**
 * OAuth token response from RevenueCat
 */
@Serializable
data class OAuthTokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("token_type") val tokenType: String,
  // expiresIn: seconds
  @SerialName("expires_in") val expiresIn: Long,
  @SerialName("refresh_token") val refreshToken: String? = null,
  @SerialName("scope") val scope: String? = null,
)

/**
 * OAuth error response
 */
@Serializable
data class OAuthErrorResponse(
  @SerialName("error") val error: String,
  @SerialName("error_description") val errorDescription: String? = null,
)
