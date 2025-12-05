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
package com.revenuecat.plugin.wizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.revenuecat.plugin.services.BlogRssService
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.net.URI
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Dialog to display recent blog articles from RevenueCat
 */
class BlogArticlesDialog(project: Project?) : DialogWrapper(project) {

  private val settings = RevenueCatSettingsState.getInstance()
  private val contentPanel = JBPanel<JBPanel<*>>(BorderLayout())
  private val notifyCheckbox =
    JBCheckBox("Notify me when new articles are published", settings.notifyBlogArticles)

  init {
    title = "RevenueCat Blog"
    setSize(800, 650)
    init()
    loadArticles()
    setupCheckboxListener()
  }

  private fun setupCheckboxListener() {
    notifyCheckbox.addActionListener {
      settings.notifyBlogArticles = notifyCheckbox.isSelected
    }
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    mainPanel.border = JBUI.Borders.empty(10)

    // Header
    val headerLabel = JBLabel("<html><h2>Recent RevenueCat Blog Posts</h2></html>")
    headerLabel.border = JBUI.Borders.emptyBottom(10)
    mainPanel.add(headerLabel, BorderLayout.NORTH)

    // Content area (will be populated with articles)
    contentPanel.border = JBUI.Borders.empty()
    showLoadingState()

    val scrollPane = JBScrollPane(contentPanel)
    scrollPane.border = JBUI.Borders.empty()
    scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    mainPanel.add(scrollPane, BorderLayout.CENTER)

    // Footer with notification checkbox
    val footerPanel = JBPanel<JBPanel<*>>(BorderLayout())
    footerPanel.border = JBUI.Borders.emptyTop(10)
    footerPanel.add(notifyCheckbox, BorderLayout.WEST)
    mainPanel.add(footerPanel, BorderLayout.SOUTH)

    return mainPanel
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction.apply { putValue(Action.NAME, "Close") })
  }

  private fun showLoadingState() {
    contentPanel.removeAll()
    contentPanel.layout = BorderLayout()

    val loadingLabel = JBLabel("Loading articles...")
    loadingLabel.horizontalAlignment = SwingConstants.CENTER
    contentPanel.add(loadingLabel, BorderLayout.CENTER)

    contentPanel.revalidate()
    contentPanel.repaint()
  }

  private fun loadArticles() {
    BlogRssService.fetchAndCacheArticles { articles ->
      SwingUtilities.invokeLater {
        displayArticles(articles)
      }
    }
  }

  private fun displayArticles(articles: List<BlogRssService.BlogArticle>) {
    contentPanel.removeAll()
    contentPanel.layout = BorderLayout()

    if (articles.isEmpty()) {
      val emptyLabel = JBLabel("No articles available")
      emptyLabel.horizontalAlignment = SwingConstants.CENTER
      contentPanel.add(emptyLabel, BorderLayout.CENTER)
    } else {
      val listPanel = JBPanel<JBPanel<*>>()
      listPanel.layout = BoxLayout(listPanel, BoxLayout.Y_AXIS)

      for (article in articles) {
        listPanel.add(createArticleCard(article))
        listPanel.add(Box.createVerticalStrut(10))
      }

      listPanel.add(Box.createVerticalGlue())
      contentPanel.add(listPanel, BorderLayout.NORTH)
    }

    contentPanel.revalidate()
    contentPanel.repaint()
  }

  private fun createArticleCard(article: BlogRssService.BlogArticle): JPanel {
    val card = JBPanel<JBPanel<*>>(BorderLayout())
    card.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(JBUI.CurrentTheme.Button.buttonOutlineColorStart(false), 1),
      JBUI.Borders.empty(12),
    )
    card.maximumSize = Dimension(Int.MAX_VALUE, 120)
    card.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    // Title
    val titleLabel = JBLabel("<html><b>${escapeHtml(article.title)}</b></html>")
    titleLabel.font = titleLabel.font.deriveFont(14f)

    // Date
    val dateLabel = JBLabel(article.publishedAt)
    dateLabel.foreground = JBColor.GRAY
    dateLabel.font = dateLabel.font.deriveFont(11f)

    // Description
    val descLabel =
      JBLabel("<html><p style='width: 650px;'>${escapeHtml(article.description)}</p></html>")
    descLabel.foreground = JBColor.GRAY
    descLabel.font = descLabel.font.deriveFont(12f)

    // Top row with title and date
    val topPanel = JBPanel<JBPanel<*>>(BorderLayout())
    topPanel.add(titleLabel, BorderLayout.CENTER)
    topPanel.add(dateLabel, BorderLayout.EAST)

    card.add(topPanel, BorderLayout.NORTH)
    card.add(descLabel, BorderLayout.CENTER)

    // Click to open in browser
    card.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent?) {
        try {
          Desktop.getDesktop().browse(URI(article.url))
        } catch (ex: Exception) {
          // Ignore
        }
      }

      override fun mouseEntered(e: java.awt.event.MouseEvent?) {
        card.border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(JBUI.CurrentTheme.Button.buttonOutlineColorStart(true), 2),
          JBUI.Borders.empty(12),
        )
      }

      override fun mouseExited(e: java.awt.event.MouseEvent?) {
        card.border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(
            JBUI.CurrentTheme.Button.buttonOutlineColorStart(false),
            1,
          ),
          JBUI.Borders.empty(12),
        )
      }
    })

    return card
  }

  private fun escapeHtml(text: String): String {
    return text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
  }
}
