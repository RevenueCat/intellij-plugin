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
package com.revenuecat.plugin.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Customer subscription information
 */
@Serializable
data class Subscription(
  @SerialName("id") val id: String,
  @SerialName("product_id") val productId: String,
  @SerialName("status") val status: String,
  @SerialName("created_at") val createdAt: String,
  @SerialName("expires_at") val expiresAt: String? = null,
  @SerialName("price") val price: Double? = null,
  @SerialName("currency") val currency: String? = null,
)

/**
 * Customer subscriptions response
 */
@Serializable
data class SubscriptionsResponse(
  @SerialName("subscriptions") val subscriptions: List<Subscription>? = null,
  @SerialName("next_page") val nextPage: String? = null,
) {
  /**
   * Get subscriptions list, never null
   */
  fun getSubscriptionsSafe(): List<Subscription> = subscriptions ?: emptyList()
}

/**
 * Purchase information
 */
@Serializable
data class Purchase(
  @SerialName("id") val id: String,
  @SerialName("product_id") val productId: String,
  @SerialName("purchased_at") val purchasedAt: String,
  @SerialName("revenue") val revenue: Double? = null,
  @SerialName("currency") val currency: String? = null,
  @SerialName("app_user_id") val appUserId: String? = null,
  @SerialName("platform") val platform: String? = null,
  @SerialName("store") val store: String? = null,
)

/**
 * Purchase response
 */
@Serializable
data class PurchaseResponse(
  @SerialName("purchase") val purchase: Purchase,
)

/**
 * Purchases list response
 */
@Serializable
data class PurchasesListResponse(
  @SerialName("purchases") val purchases: List<Purchase>? = null,
  @SerialName("next_page") val nextPage: String? = null,
) {
  fun getPurchasesSafe(): List<Purchase> = purchases ?: emptyList()
}

/**
 * Metric from overview API
 */
@Serializable
data class OverviewMetric(
  @SerialName("id") val id: String,
  @SerialName("name") val name: String,
  @SerialName("description") val description: String? = null,
  @SerialName("unit") val unit: String,
  @SerialName("period") val period: String? = null,
  @SerialName("value") val value: Double,
  @SerialName("last_updated_at") val lastUpdatedAt: String? = null,
)

/**
 * Overview metrics response
 */
@Serializable
data class OverviewMetricsResponse(
  @SerialName("metrics") val metrics: List<OverviewMetric>? = null,
) {
  fun getMetricsSafe(): List<OverviewMetric> = metrics ?: emptyList()

  fun getMetricById(id: String): OverviewMetric? = getMetricsSafe().find { it.id == id }

  fun getMRR(): Double? = getMetricById("mrr")?.value
  fun getActiveSubscriptions(): Double? = getMetricById("active_subscriptions")?.value
  fun getActiveTrials(): Double? = getMetricById("active_trials")?.value
  fun getRevenue(): Double? = getMetricById("revenue")?.value
  fun getActiveUsers(): Double? = getMetricById("active_users")?.value
  fun getNewCustomers(): Double? = getMetricById("new_customers")?.value
}

/**
 * Revenue summary (computed from metrics)
 */
data class RevenueSummary(
  val mrr: Double,
  val activeSubscriptions: Int,
  val activeTrials: Int,
  val activeUsers: Int,
  val newCustomers: Int,
  val revenue: Double,
  val currency: String,
  val lastUpdated: Long = System.currentTimeMillis(),
)

/**
 * Project information
 */
@Serializable
data class Project(
  @SerialName("id") val id: String,
  @SerialName("name") val name: String,
  @SerialName("created_at") val createdAt: Long,
  @SerialName("object") val objectType: String,
)

/**
 * Projects list response
 */
@Serializable
data class ProjectsListResponse(
  @SerialName("items") val items: List<Project>? = null,
  @SerialName("next_page") val nextPage: String? = null,
) {
  fun getProjectsSafe(): List<Project> = items ?: emptyList()
}

/**
 * Package information
 */
@Serializable
data class Package(
  @SerialName("id") val id: String,
  @SerialName("lookup_key") val lookupKey: String,
  @SerialName("display_name") val displayName: String,
  @SerialName("position") val position: Int? = null,
  @SerialName("created_at") val createdAt: Long,
  @SerialName("object") val objectType: String,
)

/**
 * Packages list
 */
@Serializable
data class PackagesList(
  @SerialName("items") val items: List<Package>? = null,
  @SerialName("next_page") val nextPage: String? = null,
) {
  fun getPackagesSafe(): List<Package> = items ?: emptyList()
}

/**
 * Offering information
 */
@Serializable
data class Offering(
  @SerialName("id") val id: String,
  @SerialName("lookup_key") val lookupKey: String,
  @SerialName("display_name") val displayName: String,
  @SerialName("is_current") val isCurrent: Boolean,
  @SerialName("created_at") val createdAt: Long,
  @SerialName("project_id") val projectId: String,
  @SerialName("metadata") val metadata: JsonObject? = null,
  @SerialName("packages") val packages: PackagesList? = null,
  @SerialName("object") val objectType: String,
  // paywall: Can be PaywallData or PaywallComponentsData
  @SerialName("paywall") val paywall: JsonElement? = null,
) {
  fun hasPaywall(): Boolean = paywall != null
}

/**
 * Offerings list response
 */
@Serializable
data class OfferingsListResponse(
  @SerialName("items") val items: List<Offering>? = null,
  @SerialName("next_page") val nextPage: String? = null,
) {
  fun getOfferingsSafe(): List<Offering> = items ?: emptyList()
}

/**
 * Subscriber offerings response (from v1 API)
 */
@Serializable
data class SubscriberOfferingsResponse(
  @SerialName("current_offering_id") val currentOfferingId: String? = null,
  @SerialName("offerings") val offerings: List<SubscriberOffering>? = null,
) {
  fun getOfferingsSafe(): List<SubscriberOffering> = offerings ?: emptyList()
}

/**
 * Subscriber offering with paywall data
 */
@Serializable
data class SubscriberOffering(
  @SerialName("identifier") val identifier: String,
  @SerialName("description") val description: String,
  @SerialName("packages") val packages: List<SubscriberPackage>? = null,
  // paywall: PaywallData (old template system)
  @SerialName("paywall") val paywall: JsonElement? = null,
  // paywallComponents: PaywallComponents (new system)
  @SerialName("paywall_components") val paywallComponents: JsonElement? = null,
  @SerialName("metadata") val metadata: JsonObject? = null,
) {
  fun hasPaywall(): Boolean = paywall != null || paywallComponents != null
}

/**
 * Subscriber package
 */
@Serializable
data class SubscriberPackage(
  @SerialName("identifier") val identifier: String,
  @SerialName("platform_product_identifier") val platformProductIdentifier: String,
)

/**
 * Response from creating a paywall
 */
@Serializable
data class CreatePaywallResponse(
  // id: This is the paywall ID (e.g., "pw8426cd6a86374b3f")
  @SerialName("id") val id: String,
  @SerialName("name") val name: String? = null,
  @SerialName("offering_id") val offeringId: String? = null,
  @SerialName("template_name") val templateName: String? = null,
  @SerialName("created_at") val createdAt: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * API error response
 */
@Serializable
data class ApiError(
  @SerialName("message") val message: String,
  @SerialName("code") val code: Int? = null,
)
