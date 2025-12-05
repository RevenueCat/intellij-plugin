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
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Service to fetch and cache blog articles from RevenueCat RSS feed
 */
object BlogRssService {
  private val LOG = Logger.getInstance(BlogRssService::class.java)
  private val json = Json { ignoreUnknownKeys = true }

  private const val RSS_URL = "https://www.revenuecat.com/blog/rss.xml"
  private const val MAX_CACHED_ARTICLES = 8

  @Serializable
  data class BlogArticle(
    val title: String,
    val url: String,
    val description: String,
    val publishedAt: String,
    val imageUrl: String = "",
  )

  /**
   * Fetch articles from RSS feed (English only) and merge with cache
   */
  fun fetchAndCacheArticles(callback: (List<BlogArticle>) -> Unit) {
    Thread {
      try {
        val newArticles = fetchArticlesFromRss()
        val cachedArticles = getCachedArticles()

        // Merge new articles with cached ones (no duplicates)
        val mergedArticles = mergeArticles(newArticles, cachedArticles)

        // Save to cache
        saveCachedArticles(mergedArticles)

        callback(mergedArticles)
      } catch (e: Exception) {
        LOG.warn("Failed to fetch blog articles: ${e.message}")
        // Return cached articles on error
        callback(getCachedArticles())
      }
    }.start()
  }

  /**
   * Fetch articles from RSS feed (blocking)
   */
  private fun fetchArticlesFromRss(): List<BlogArticle> {
    val url = URL(RSS_URL)
    val connection = url.openConnection() as HttpURLConnection

    try {
      connection.requestMethod = "GET"
      connection.setRequestProperty("User-Agent", "RevenueCat-IntelliJ-Plugin")
      connection.connectTimeout = 10000
      connection.readTimeout = 10000

      if (connection.responseCode == HttpURLConnection.HTTP_OK) {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(connection.inputStream)

        val items = document.getElementsByTagName("item")
        val articles = mutableListOf<BlogArticle>()

        for (i in 0 until items.length) {
          val item = items.item(i) as Element
          val articleUrl = getElementText(item, "link")

          // Filter out Japanese articles (URLs containing /jp/)
          if (articleUrl.contains("/jp/")) {
            continue
          }

          val title = getElementText(item, "title")
          val description = cleanDescription(getElementText(item, "description"))
          val pubDate = getElementText(item, "pubDate")
          val imageUrl = getEnclosureUrl(item)

          articles.add(
            BlogArticle(
              title = title,
              url = articleUrl,
              description = description,
              publishedAt = formatDate(pubDate),
              imageUrl = imageUrl,
            ),
          )
        }

        return articles
      } else {
        throw Exception("HTTP ${connection.responseCode}")
      }
    } finally {
      connection.disconnect()
    }
  }

  private fun getElementText(parent: Element, tagName: String): String {
    val nodeList: NodeList = parent.getElementsByTagName(tagName)
    return if (nodeList.length > 0) {
      nodeList.item(0).textContent ?: ""
    } else {
      ""
    }
  }

  private fun getEnclosureUrl(item: Element): String {
    val enclosures = item.getElementsByTagName("enclosure")
    return if (enclosures.length > 0) {
      (enclosures.item(0) as Element).getAttribute("url") ?: ""
    } else {
      ""
    }
  }

  private fun cleanDescription(description: String): String {
    // Remove HTML tags and limit length
    return description
      .replace(Regex("<[^>]*>"), "")
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .trim()
      .take(200)
      .let { if (it.length == 200) "$it..." else it }
  }

  private fun formatDate(rssDate: String): String {
    if (rssDate.isEmpty()) return ""
    return try {
      // Parse RSS date format: "Wed, 26 Nov 2025 23:20:08 GMT"
      val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
      val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
      val date = inputFormat.parse(rssDate)
      outputFormat.format(date!!)
    } catch (e: Exception) {
      rssDate.substringBefore(" GMT").substringAfter(", ")
    }
  }

  private fun mergeArticles(
    newArticles: List<BlogArticle>,
    cachedArticles: List<BlogArticle>,
  ): List<BlogArticle> {
    val urlSet = mutableSetOf<String>()
    val merged = mutableListOf<BlogArticle>()

    // Add new articles first
    for (article in newArticles) {
      if (article.url !in urlSet) {
        urlSet.add(article.url)
        merged.add(article)
      }
    }

    // Add cached articles that are not duplicates
    for (article in cachedArticles) {
      if (article.url !in urlSet) {
        urlSet.add(article.url)
        merged.add(article)
      }
    }

    // Sort by date (newest first) and limit to MAX_CACHED_ARTICLES
    return merged
      .sortedByDescending { parseDate(it.publishedAt) }
      .take(MAX_CACHED_ARTICLES)
  }

  private fun parseDate(dateStr: String): Long {
    return try {
      val format = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
      format.parse(dateStr)?.time ?: 0L
    } catch (e: Exception) {
      0L
    }
  }

  /**
   * Get cached articles from settings
   */
  fun getCachedArticles(): List<BlogArticle> {
    return try {
      val settings = RevenueCatSettingsState.getInstance()
      json.decodeFromString<List<BlogArticle>>(settings.cachedBlogArticles)
    } catch (e: Exception) {
      emptyList()
    }
  }

  /**
   * Save articles to cache
   */
  private fun saveCachedArticles(articles: List<BlogArticle>) {
    try {
      val settings = RevenueCatSettingsState.getInstance()
      settings.cachedBlogArticles = json.encodeToString(articles)

      // Update last known article URL
      if (articles.isNotEmpty()) {
        settings.lastKnownBlogArticleUrl = articles.first().url
      }
    } catch (e: Exception) {
      LOG.warn("Failed to save cached articles: ${e.message}")
    }
  }

  /**
   * Check if there are new articles since last check
   */
  fun hasNewArticles(articles: List<BlogArticle>): Boolean {
    val settings = RevenueCatSettingsState.getInstance()
    if (settings.lastKnownBlogArticleUrl.isEmpty() || articles.isEmpty()) {
      return false
    }
    return articles.first().url != settings.lastKnownBlogArticleUrl
  }

  /**
   * Get the newest article that wasn't seen before
   */
  fun getNewArticle(articles: List<BlogArticle>): BlogArticle? {
    val settings = RevenueCatSettingsState.getInstance()
    if (settings.lastKnownBlogArticleUrl.isEmpty() || articles.isEmpty()) {
      return null
    }
    val newest = articles.firstOrNull()
    return if (newest != null && newest.url != settings.lastKnownBlogArticleUrl) {
      newest
    } else {
      null
    }
  }
}
