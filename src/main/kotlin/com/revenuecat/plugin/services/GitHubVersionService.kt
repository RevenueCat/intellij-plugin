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

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service to fetch the latest RevenueCat SDK versions from GitHub releases
 */
object GitHubVersionService {
  private val LOG = Logger.getInstance(GitHubVersionService::class.java)
  private val json = Json { ignoreUnknownKeys = true }

  // Default fallback versions
  private const val DEFAULT_ANDROID_VERSION = "9.15.1"
  private const val DEFAULT_KMP_VERSION = "2.2.11+17.21.0"
  private const val DEFAULT_FLUTTER_VERSION = "9.9.9"
  private const val DEFAULT_IOS_VERSION = "5.18.0"
  private const val DEFAULT_REACT_NATIVE_VERSION = "8.10.0"
  private const val DEFAULT_ANDROID_UI_VERSION = "2.5.0"
  private const val DEFAULT_KMP_UI_VERSION = "2.5.0"
  private const val DEFAULT_FLUTTER_UI_VERSION = "0.3.0"

  // Cache for versions (to avoid repeated API calls)
  private var cachedAndroidVersion: String? = null
  private var cachedKmpVersion: String? = null
  private var cachedFlutterVersion: String? = null
  private var cachedIosVersion: String? = null
  private var cachedReactNativeVersion: String? = null
  private var cachedAndroidUiVersion: String? = null
  private var cachedKmpUiVersion: String? = null
  private var cachedFlutterUiVersion: String? = null
  private var lastFetchTime: Long = 0
  private const val CACHE_DURATION_MS = 5 * 60 * 1000 // 5 minutes

  // Cache for release info
  private var cachedAndroidRelease: ReleaseInfo? = null
  private var cachedKmpRelease: ReleaseInfo? = null
  private var cachedFlutterRelease: ReleaseInfo? = null
  private var cachedIosRelease: ReleaseInfo? = null
  private var cachedReactNativeRelease: ReleaseInfo? = null

  @Serializable
  private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
  )

  /**
   * Data class to hold release information
   */
  data class ReleaseInfo(
    val version: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val url: String,
  )

  /**
   * Fetch the latest Android SDK version from GitHub
   */
  fun getLatestAndroidVersion(): String {
    return getCachedOrFetch(
      cached = cachedAndroidVersion,
      repoUrl = "https://api.github.com/repos/RevenueCat/purchases-android/releases/latest",
      default = DEFAULT_ANDROID_VERSION,
    ) { version -> cachedAndroidVersion = version }
  }

  /**
   * Fetch the latest KMP SDK version from GitHub
   */
  fun getLatestKmpVersion(): String {
    return getCachedOrFetch(
      cached = cachedKmpVersion,
      repoUrl = "https://api.github.com/repos/RevenueCat/purchases-kmp/releases/latest",
      default = DEFAULT_KMP_VERSION,
    ) { version -> cachedKmpVersion = version }
  }

  /**
   * Fetch the latest Flutter SDK version from GitHub
   */
  fun getLatestFlutterVersion(): String {
    return getCachedOrFetch(
      cached = cachedFlutterVersion,
      repoUrl = "https://api.github.com/repos/RevenueCat/purchases-flutter/releases/latest",
      default = DEFAULT_FLUTTER_VERSION,
    ) { version -> cachedFlutterVersion = version }
  }

  /**
   * Fetch the latest iOS SDK version from GitHub
   */
  fun getLatestIosVersion(): String {
    return getCachedOrFetch(
      cached = cachedIosVersion,
      repoUrl = "https://api.github.com/repos/RevenueCat/purchases-ios/releases/latest",
      default = DEFAULT_IOS_VERSION,
    ) { version -> cachedIosVersion = version }
  }

  /**
   * Fetch the latest React Native SDK version from GitHub
   */
  fun getLatestReactNativeVersion(): String {
    return getCachedOrFetch(
      cached = cachedReactNativeVersion,
      repoUrl = "https://api.github.com/repos/RevenueCat/react-native-purchases/releases/latest",
      default = DEFAULT_REACT_NATIVE_VERSION,
    ) { version -> cachedReactNativeVersion = version }
  }

  /**
   * Fetch the latest Android UI SDK version from GitHub
   */
  fun getLatestAndroidUiVersion(): String {
    return getCachedOrFetch(
      cached = cachedAndroidUiVersion,
      repoUrl = "https://api.github.com/repos/RevenueCat/purchases-android/releases/latest",
      default = DEFAULT_ANDROID_UI_VERSION,
    ) { version -> cachedAndroidUiVersion = version }
  }

  /**
   * Fetch the latest KMP UI SDK version from GitHub
   */
  fun getLatestKmpUiVersion(): String {
    return getCachedOrFetch(
      cached = cachedKmpUiVersion,
      repoUrl = "https://api.github.com/repos/RevenueCat/purchases-kmp/releases/latest",
      default = DEFAULT_KMP_UI_VERSION,
    ) { version -> cachedKmpUiVersion = version }
  }

  /**
   * Fetch the latest Flutter UI SDK version from GitHub
   */
  fun getLatestFlutterUiVersion(): String {
    return getCachedOrFetch(
      cached = cachedFlutterUiVersion,
      repoUrl = "https://api.github.com/repos/RevenueCat/purchases-flutter/releases/latest",
      default = DEFAULT_FLUTTER_UI_VERSION,
    ) { version -> cachedFlutterUiVersion = version }
  }

  private fun getCachedOrFetch(
    cached: String?,
    repoUrl: String,
    default: String,
    cacheUpdate: (String) -> Unit,
  ): String {
    val now = System.currentTimeMillis()

    // Return cached version if still valid
    if (cached != null && (now - lastFetchTime) < CACHE_DURATION_MS) {
      return cached
    }

    return try {
      val version = fetchLatestVersionFromGitHub(repoUrl)
      cacheUpdate(version)
      lastFetchTime = now
      version
    } catch (e: Exception) {
      LOG.warn("Failed to fetch latest version from $repoUrl: ${e.message}")
      cached ?: default
    }
  }

  private fun fetchLatestVersionFromGitHub(apiUrl: String): String {
    val url = URL(apiUrl)
    val connection = url.openConnection() as HttpURLConnection

    try {
      connection.requestMethod = "GET"
      connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
      connection.setRequestProperty("User-Agent", "RevenueCat-IntelliJ-Plugin")
      connection.connectTimeout = 5000
      connection.readTimeout = 5000

      if (connection.responseCode == HttpURLConnection.HTTP_OK) {
        val response = connection.inputStream.bufferedReader().readText()
        val release = json.decodeFromString<GitHubRelease>(response)
        // Remove 'v' prefix if present
        return release.tagName.removePrefix("v")
      } else {
        throw Exception("HTTP ${connection.responseCode}")
      }
    } finally {
      connection.disconnect()
    }
  }

  private fun fetchReleaseFromGitHub(apiUrl: String): ReleaseInfo? {
    val url = URL(apiUrl)
    val connection = url.openConnection() as HttpURLConnection

    try {
      connection.requestMethod = "GET"
      connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
      connection.setRequestProperty("User-Agent", "RevenueCat-IntelliJ-Plugin")
      connection.connectTimeout = 5000
      connection.readTimeout = 5000

      if (connection.responseCode == HttpURLConnection.HTTP_OK) {
        val response = connection.inputStream.bufferedReader().readText()
        val release = json.decodeFromString<GitHubRelease>(response)
        return ReleaseInfo(
          version = release.tagName.removePrefix("v"),
          name = release.name ?: release.tagName,
          body = release.body ?: "",
          publishedAt = release.publishedAt ?: "",
          url = release.htmlUrl ?: "",
        )
      } else {
        throw Exception("HTTP ${connection.responseCode}")
      }
    } finally {
      connection.disconnect()
    }
  }

  /**
   * Fetch the latest Android release info from GitHub
   */
  fun getAndroidReleaseInfo(): ReleaseInfo? {
    val now = System.currentTimeMillis()
    if (cachedAndroidRelease != null && (now - lastFetchTime) < CACHE_DURATION_MS) {
      return cachedAndroidRelease
    }
    return try {
      val release =
        fetchReleaseFromGitHub(
          "https://api.github.com/repos/RevenueCat/purchases-android/releases/latest",
        )
      cachedAndroidRelease = release
      release
    } catch (e: Exception) {
      LOG.warn("Failed to fetch Android release info: ${e.message}")
      cachedAndroidRelease
    }
  }

  /**
   * Fetch the latest KMP release info from GitHub
   */
  fun getKmpReleaseInfo(): ReleaseInfo? {
    val now = System.currentTimeMillis()
    if (cachedKmpRelease != null && (now - lastFetchTime) < CACHE_DURATION_MS) {
      return cachedKmpRelease
    }
    return try {
      val release =
        fetchReleaseFromGitHub(
          "https://api.github.com/repos/RevenueCat/purchases-kmp/releases/latest",
        )
      cachedKmpRelease = release
      release
    } catch (e: Exception) {
      LOG.warn("Failed to fetch KMP release info: ${e.message}")
      cachedKmpRelease
    }
  }

  /**
   * Fetch the latest Flutter release info from GitHub
   */
  fun getFlutterReleaseInfo(): ReleaseInfo? {
    val now = System.currentTimeMillis()
    if (cachedFlutterRelease != null && (now - lastFetchTime) < CACHE_DURATION_MS) {
      return cachedFlutterRelease
    }
    return try {
      val release =
        fetchReleaseFromGitHub(
          "https://api.github.com/repos/RevenueCat/purchases-flutter/releases/latest",
        )
      cachedFlutterRelease = release
      release
    } catch (e: Exception) {
      LOG.warn("Failed to fetch Flutter release info: ${e.message}")
      cachedFlutterRelease
    }
  }

  /**
   * Fetch the latest iOS release info from GitHub
   */
  fun getIosReleaseInfo(): ReleaseInfo? {
    val now = System.currentTimeMillis()
    if (cachedIosRelease != null && (now - lastFetchTime) < CACHE_DURATION_MS) {
      return cachedIosRelease
    }
    return try {
      val release =
        fetchReleaseFromGitHub(
          "https://api.github.com/repos/RevenueCat/purchases-ios/releases/latest",
        )
      cachedIosRelease = release
      release
    } catch (e: Exception) {
      LOG.warn("Failed to fetch iOS release info: ${e.message}")
      cachedIosRelease
    }
  }

  /**
   * Fetch the latest React Native release info from GitHub
   */
  fun getReactNativeReleaseInfo(): ReleaseInfo? {
    val now = System.currentTimeMillis()
    if (cachedReactNativeRelease != null && (now - lastFetchTime) < CACHE_DURATION_MS) {
      return cachedReactNativeRelease
    }
    return try {
      val release =
        fetchReleaseFromGitHub(
          "https://api.github.com/repos/RevenueCat/react-native-purchases/releases/latest",
        )
      cachedReactNativeRelease = release
      release
    } catch (e: Exception) {
      LOG.warn("Failed to fetch React Native release info: ${e.message}")
      cachedReactNativeRelease
    }
  }

  /**
   * Fetch all release info asynchronously
   */
  fun fetchAllReleaseInfo(
    callback: (
      android: ReleaseInfo?,
      kmp: ReleaseInfo?,
      flutter: ReleaseInfo?,
      ios: ReleaseInfo?,
      reactNative: ReleaseInfo?,
    ) -> Unit,
  ) {
    Thread {
      try {
        val android = getAndroidReleaseInfo()
        val kmp = getKmpReleaseInfo()
        val flutter = getFlutterReleaseInfo()
        val ios = getIosReleaseInfo()
        val reactNative = getReactNativeReleaseInfo()
        callback(android, kmp, flutter, ios, reactNative)
      } catch (e: Exception) {
        LOG.warn("Failed to fetch release info: ${e.message}")
        callback(null, null, null, null, null)
      }
    }.start()
  }

  /**
   * Fetch all versions asynchronously (for preloading)
   */
  fun preloadVersions(callback: () -> Unit = {}) {
    Thread {
      try {
        getLatestAndroidVersion()
        getLatestKmpVersion()
        getLatestFlutterVersion()
        callback()
      } catch (e: Exception) {
        LOG.warn("Failed to preload versions: ${e.message}")
      }
    }.start()
  }

  /**
   * Clear the cache (useful for forcing a refresh)
   */
  fun clearCache() {
    cachedAndroidVersion = null
    cachedKmpVersion = null
    cachedFlutterVersion = null
    cachedIosVersion = null
    cachedReactNativeVersion = null
    cachedAndroidUiVersion = null
    cachedKmpUiVersion = null
    cachedFlutterUiVersion = null
    cachedAndroidRelease = null
    cachedKmpRelease = null
    cachedFlutterRelease = null
    cachedIosRelease = null
    cachedReactNativeRelease = null
    lastFetchTime = 0
  }
}
