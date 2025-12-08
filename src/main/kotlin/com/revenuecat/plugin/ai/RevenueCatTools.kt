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
package com.revenuecat.plugin.ai

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.revenuecat.plugin.api.RevenueCatApiClient
import com.revenuecat.plugin.settings.RevenueCatSettingsState

/**
 * RevenueCat tools for the AI agent to interact with RevenueCat APIs
 */
@LLMDescription("Tools for interacting with RevenueCat subscription analytics and management")
class RevenueCatTools : ToolSet {

  private val settings = RevenueCatSettingsState.getInstance()

  @Tool
  @LLMDescription(
    "Get the current subscription metrics including MRR, active trials, active subscriptions, and revenue",
  )
  fun getMetrics(): String {
    if (!settings.isConfigured()) {
      return "Error: RevenueCat API is not configured. Please configure your API key in settings."
    }

    val apiClient = RevenueCatApiClient(settings.apiKey, settings.projectId)
    val result = apiClient.getOverviewMetrics(settings.projectId)

    return result.fold(
      onSuccess = { overview ->
        buildString {
          appendLine("## RevenueCat Metrics Overview")
          appendLine()
          val mrr = overview.getMRR() ?: 0.0
          val activeTrials = overview.getActiveTrials()?.toInt() ?: 0
          val activeSubscriptions = overview.getActiveSubscriptions()?.toInt() ?: 0
          val revenue = overview.getRevenue() ?: 0.0
          appendLine("- **Monthly Recurring Revenue (MRR)**: $${String.format("%.2f", mrr)}")
          appendLine("- **Active Trials**: $activeTrials")
          appendLine("- **Active Subscriptions**: $activeSubscriptions")
          appendLine("- **Revenue**: $${String.format("%.2f", revenue)}")
        }
      },
      onFailure = { error ->
        "Error fetching metrics: ${error.message}"
      },
    )
  }

  @Tool
  @LLMDescription(
    "Get the list of offerings configured in RevenueCat including packages and products",
  )
  fun getOfferings(): String {
    if (!settings.isConfigured()) {
      return "Error: RevenueCat API is not configured. Please configure your API key in settings."
    }

    val apiClient = RevenueCatApiClient(settings.apiKey, settings.projectId)
    val result = apiClient.getOfferings(settings.projectId)

    return result.fold(
      onSuccess = { offeringsResponse ->
        val offerings = offeringsResponse.getOfferingsSafe()
        if (offerings.isEmpty()) {
          "No offerings found in your RevenueCat project."
        } else {
          buildString {
            appendLine("## RevenueCat Offerings")
            appendLine()
            offerings.forEach { offering ->
              appendLine("### ${offering.lookupKey}")
              if (offering.displayName.isNotEmpty()) {
                appendLine("Display Name: ${offering.displayName}")
              }
              if (offering.isCurrent) {
                appendLine("**(Current Offering)**")
              }
              appendLine()
              val packages = offering.packages?.getPackagesSafe() ?: emptyList()
              if (packages.isNotEmpty()) {
                appendLine("**Packages:**")
                packages.forEach { pkg ->
                  appendLine("- ${pkg.lookupKey}: ${pkg.displayName}")
                }
              }
              appendLine()
            }
          }
        }
      },
      onFailure = { error ->
        "Error fetching offerings: ${error.message}"
      },
    )
  }

  @Tool
  @LLMDescription("Get the project configuration status and API key information")
  fun getProjectStatus(): String {
    return buildString {
      appendLine("## Project Configuration Status")
      appendLine()
      if (settings.isConfigured()) {
        appendLine("- **API Key**: Configured (${settings.apiKey.take(10)}...)")
        appendLine("- **Project ID**: ${settings.projectId}")
        appendLine(
          "- **SDK API Key**: ${if (settings.sdkApiKey.isNotEmpty()) "Configured" else "Not configured"}",
        )
        appendLine(
          "- **Notifications**: ${if (settings.enableNotifications) "Enabled" else "Disabled"}",
        )
        appendLine(
          "- **Webhook**: ${if (settings.enableWebhook) "Enabled on port ${settings.webhookPort}" else "Disabled"}",
        )
      } else {
        appendLine("**Not configured.** Please set up your RevenueCat API credentials in Settings.")
      }
    }
  }

  @Tool
  @LLMDescription("Get help and documentation links for RevenueCat SDK integration")
  fun getHelpLinks(): String {
    return buildString {
      appendLine("## RevenueCat Resources")
      appendLine()
      appendLine("### Documentation")
      appendLine("- [Getting Started Guide](https://www.revenuecat.com/docs/getting-started)")
      appendLine("- [Android SDK Docs](https://www.revenuecat.com/docs/android)")
      appendLine("- [iOS SDK Docs](https://www.revenuecat.com/docs/ios)")
      appendLine("- [Flutter SDK Docs](https://www.revenuecat.com/docs/flutter)")
      appendLine(
        "- [Kotlin Multiplatform Docs](https://www.revenuecat.com/docs/kotlin-multiplatform)",
      )
      appendLine()
      appendLine("### Support")
      appendLine("- [Community Forum](https://community.revenuecat.com/)")
      appendLine("- [GitHub Issues](https://github.com/RevenueCat/purchases-android/issues)")
      appendLine()
      appendLine("### Tools")
      appendLine("- [RevenueCat Dashboard](https://app.revenuecat.com/)")
      appendLine("- [API Reference](https://www.revenuecat.com/docs/api-v2)")
    }
  }
}
