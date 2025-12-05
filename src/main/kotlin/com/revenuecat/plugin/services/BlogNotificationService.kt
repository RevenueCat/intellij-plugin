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
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import com.revenuecat.plugin.wizard.BlogArticlesDialog
import java.awt.Desktop
import java.net.URI

/**
 * Service that checks for new blog articles on IDE startup and shows notifications
 */
class BlogNotificationService : ProjectActivity {

  override suspend fun execute(project: Project) {
    val settings = RevenueCatSettingsState.getInstance()

    // Only check if notifications are enabled
    if (!settings.notifyBlogArticles) {
      return
    }

    // Fetch and check for new articles
    checkForNewArticles(project)
  }

  private fun checkForNewArticles(project: Project) {
    Thread {
      try {
        BlogRssService.fetchAndCacheArticles { articles ->
          if (articles.isNotEmpty()) {
            val newArticle = BlogRssService.getNewArticle(articles)
            if (newArticle != null) {
              showNewArticleNotification(project, newArticle)
            }
          }
        }
      } catch (e: Exception) {
        // Silently fail - don't bother user with errors for background checks
      }
    }.start()
  }

  private fun showNewArticleNotification(project: Project, article: BlogRssService.BlogArticle) {
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup("RevenueCat Notifications")
      .createNotification(
        "New RevenueCat Blog Article",
        "<b>${article.title}</b><br>${article.description}",
        NotificationType.INFORMATION,
      )
      .addAction(object : com.intellij.notification.NotificationAction("Read Article") {
        override fun actionPerformed(
          e: com.intellij.openapi.actionSystem.AnActionEvent,
          notification: com.intellij.notification.Notification,
        ) {
          try {
            Desktop.getDesktop().browse(URI(article.url))
          } catch (ex: Exception) {
            // Ignore
          }
          notification.expire()
        }
      })
      .addAction(object : com.intellij.notification.NotificationAction("View All Articles") {
        override fun actionPerformed(
          e: com.intellij.openapi.actionSystem.AnActionEvent,
          notification: com.intellij.notification.Notification,
        ) {
          BlogArticlesDialog(project).show()
          notification.expire()
        }
      })

    notification.notify(project)

    // Update last known article URL so we don't notify again
    val settings = RevenueCatSettingsState.getInstance()
    settings.lastKnownBlogArticleUrl = article.url
  }
}
