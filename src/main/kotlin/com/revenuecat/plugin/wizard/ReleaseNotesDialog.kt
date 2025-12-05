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

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.revenuecat.plugin.services.GitHubVersionService
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Desktop
import java.net.URI
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * Dialog to display release notes for Android, KMP, Flutter, iOS, and React Native SDKs
 */
class ReleaseNotesDialog(project: Project?) : DialogWrapper(project) {

  private val settings = RevenueCatSettingsState.getInstance()
  private val tabbedPane = JBTabbedPane()
  private val androidPanel = createLoadingPanel()
  private val kmpPanel = createLoadingPanel()
  private val flutterPanel = createLoadingPanel()
  private val iosPanel = createLoadingPanel()
  private val reactNativePanel = createLoadingPanel()

  // Notification checkboxes
  private val androidNotifyCheckbox =
    JBCheckBox("Notify me when a new version is available", settings.notifyAndroidSdkUpdates)
  private val kmpNotifyCheckbox =
    JBCheckBox("Notify me when a new version is available", settings.notifyKmpSdkUpdates)
  private val flutterNotifyCheckbox =
    JBCheckBox("Notify me when a new version is available", settings.notifyFlutterSdkUpdates)
  private val iosNotifyCheckbox =
    JBCheckBox("Notify me when a new version is available", settings.notifyIosSdkUpdates)
  private val reactNativeNotifyCheckbox =
    JBCheckBox("Notify me when a new version is available", settings.notifyReactNativeSdkUpdates)

  init {
    title = "RevenueCat SDK Release Notes"
    setSize(850, 650)
    init()
    loadReleaseNotes()
    setupCheckboxListeners()
  }

  private fun setupCheckboxListeners() {
    androidNotifyCheckbox.addActionListener {
      settings.notifyAndroidSdkUpdates = androidNotifyCheckbox.isSelected
    }
    kmpNotifyCheckbox.addActionListener {
      settings.notifyKmpSdkUpdates = kmpNotifyCheckbox.isSelected
    }
    flutterNotifyCheckbox.addActionListener {
      settings.notifyFlutterSdkUpdates = flutterNotifyCheckbox.isSelected
    }
    iosNotifyCheckbox.addActionListener {
      settings.notifyIosSdkUpdates = iosNotifyCheckbox.isSelected
    }
    reactNativeNotifyCheckbox.addActionListener {
      settings.notifyReactNativeSdkUpdates = reactNativeNotifyCheckbox.isSelected
    }
  }

  override fun createCenterPanel(): JComponent {
    val panel = JBPanel<JBPanel<*>>(BorderLayout())
    panel.border = JBUI.Borders.empty(10)

    tabbedPane.addTab("Android", AllIcons.Nodes.Module, androidPanel)
    tabbedPane.addTab("iOS", AllIcons.Nodes.Module, iosPanel)
    tabbedPane.addTab("Kotlin Multiplatform", AllIcons.Nodes.Module, kmpPanel)
    tabbedPane.addTab("Flutter", AllIcons.Nodes.Module, flutterPanel)
    tabbedPane.addTab("React Native", AllIcons.Nodes.Module, reactNativePanel)

    panel.add(tabbedPane, BorderLayout.CENTER)
    return panel
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction.apply { putValue(Action.NAME, "Close") })
  }

  private fun createLoadingPanel(): JPanel {
    val panel = JBPanel<JBPanel<*>>(BorderLayout())
    panel.border = JBUI.Borders.empty(20)

    val loadingLabel = JBLabel("Loading release notes...")
    loadingLabel.horizontalAlignment = SwingConstants.CENTER
    panel.add(loadingLabel, BorderLayout.CENTER)

    return panel
  }

  private fun loadReleaseNotes() {
    GitHubVersionService.fetchAllReleaseInfo { android, kmp, flutter, ios, reactNative ->
      SwingUtilities.invokeLater {
        updatePanel(androidPanel, android, "Android", androidNotifyCheckbox)
        updatePanel(iosPanel, ios, "iOS", iosNotifyCheckbox)
        updatePanel(kmpPanel, kmp, "Kotlin Multiplatform", kmpNotifyCheckbox)
        updatePanel(flutterPanel, flutter, "Flutter", flutterNotifyCheckbox)
        updatePanel(reactNativePanel, reactNative, "React Native", reactNativeNotifyCheckbox)

        // Update last known versions when user views release notes
        android?.let { settings.lastKnownAndroidVersion = it.version }
        kmp?.let { settings.lastKnownKmpVersion = it.version }
        flutter?.let { settings.lastKnownFlutterVersion = it.version }
        ios?.let { settings.lastKnownIosVersion = it.version }
        reactNative?.let { settings.lastKnownReactNativeVersion = it.version }
      }
    }
  }

  private fun updatePanel(
    panel: JPanel,
    releaseInfo: GitHubVersionService.ReleaseInfo?,
    platformName: String,
    notifyCheckbox: JBCheckBox,
  ) {
    panel.removeAll()
    panel.layout = BorderLayout()
    panel.border = JBUI.Borders.empty(15)

    if (releaseInfo == null) {
      val errorLabel = JBLabel("Failed to load release notes for $platformName")
      errorLabel.horizontalAlignment = SwingConstants.CENTER
      panel.add(errorLabel, BorderLayout.CENTER)

      // Still add checkbox at bottom
      val footerPanel = JBPanel<JBPanel<*>>(BorderLayout())
      footerPanel.border = JBUI.Borders.emptyTop(10)
      footerPanel.add(notifyCheckbox, BorderLayout.WEST)
      panel.add(footerPanel, BorderLayout.SOUTH)
    } else {
      // Header panel at the top
      val headerPanel = JBPanel<JBPanel<*>>(BorderLayout())
      headerPanel.border = JBUI.Borders.emptyBottom(10)

      // Version header
      val versionLabel =
        JBLabel("<html><h2 style='font-size: 16pt;'>${releaseInfo.name}</h2></html>")
      headerPanel.add(versionLabel, BorderLayout.WEST)

      // Published date
      val dateLabel =
        JBLabel(
          "<html><span style='font-size: 11pt;'>${formatDate(
            releaseInfo.publishedAt,
          )}</span></html>",
        )
      dateLabel.foreground = JBColor.GRAY
      headerPanel.add(dateLabel, BorderLayout.EAST)

      // GitHub link below header
      val linkLabel =
        JBLabel(
          "<html><a href='${releaseInfo.url}' style='font-size: 11pt;'>View on GitHub</a></html>",
        )
      linkLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      linkLabel.border = JBUI.Borders.emptyTop(5)
      linkLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
        override fun mouseClicked(e: java.awt.event.MouseEvent?) {
          try {
            Desktop.getDesktop().browse(URI(releaseInfo.url))
          } catch (ex: Exception) {
            // Ignore
          }
        }
      })
      headerPanel.add(linkLabel, BorderLayout.SOUTH)

      panel.add(headerPanel, BorderLayout.NORTH)

      // Release notes as plain text
      val textArea = JTextArea(releaseInfo.body)
      textArea.isEditable = false
      textArea.lineWrap = true
      textArea.wrapStyleWord = true
      textArea.background = panel.background
      textArea.font = textArea.font.deriveFont(13f)
      textArea.caretPosition = 0

      val scrollPane = JBScrollPane(textArea)
      scrollPane.border = JBUI.Borders.empty()
      panel.add(scrollPane, BorderLayout.CENTER)

      // Footer with notification checkbox
      val footerPanel = JBPanel<JBPanel<*>>(BorderLayout())
      footerPanel.border = JBUI.Borders.emptyTop(10)
      footerPanel.add(notifyCheckbox, BorderLayout.WEST)
      panel.add(footerPanel, BorderLayout.SOUTH)
    }

    panel.revalidate()
    panel.repaint()
  }

  private fun formatDate(isoDate: String): String {
    if (isoDate.isEmpty()) return ""
    return try {
      // Parse ISO date format: 2024-11-27T17:37:00Z
      val date = isoDate.substringBefore("T")
      "Published: $date"
    } catch (e: Exception) {
      isoDate
    }
  }
}
