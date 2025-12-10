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
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import com.revenuecat.plugin.wizard.BlogArticlesDialog
import java.awt.Desktop
import java.net.URI
import java.util.Timer
import java.util.TimerTask

/**
 * Service that checks for new blog articles periodically and shows notifications
 */
class BlogNotificationService : ProjectActivity {

  companion object {
    // Check every 30 minutes (in milliseconds)
    private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L

    // Initial delay of 10 seconds after startup
    private const val INITIAL_DELAY_MS = 10 * 1000L

    // Track if periodic check is already started (to avoid multiple timers across projects)
    @Volatile
    private var isPeriodicCheckStarted = false
  }

  override suspend fun execute(project: Project) {
    val settings = RevenueCatSettingsState.getInstance()

    // Only start periodic checking if notifications are enabled
    if (!settings.notifyBlogArticles) {
      return
    }

    // Only start one timer globally (avoid multiple notifications when multiple projects are open)
    synchronized(this) {
      if (isPeriodicCheckStarted) {
        return
      }
      isPeriodicCheckStarted = true
    }

    // Start periodic checking
    startPeriodicCheck(project)
  }

  private fun startPeriodicCheck(project: Project) {
    val timer = Timer("BlogNotificationChecker", true)
    val disposable = Disposable { timer.cancel() }

    // Register disposable to cancel timer when project closes
    Disposer.register(project, disposable)

    timer.scheduleAtFixedRate(
      object : TimerTask() {
        override fun run() {
          val settings = RevenueCatSettingsState.getInstance()
          if (settings.notifyBlogArticles) {
            checkForNewArticles(project)
          }
        }
      },
      INITIAL_DELAY_MS,
      CHECK_INTERVAL_MS,
    )
  }

  private fun checkForNewArticles(project: Project) {
    BlogRssService.fetchAndCacheArticles { articles ->
      if (articles.isNotEmpty()) {
        val newArticle = BlogRssService.getNewArticle(articles)
        if (newArticle != null) {
          showNewArticleNotification(project, newArticle, articles)
        }
      }
    }
  }

  private fun showNewArticleNotification(
    project: Project,
    article: BlogRssService.BlogArticle,
    allArticles: List<BlogRssService.BlogArticle>,
  ) {
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

    // Mark the newest article as seen so we don't notify again
    BlogRssService.markNewestArticleAsSeen(allArticles)
  }
}
