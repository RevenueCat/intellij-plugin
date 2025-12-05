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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.revenuecat.plugin.services.GitHubVersionService
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Configuration panel for Kotlin Multiplatform Paywall UI integration
 */
class KmpPaywallConfigPanel(
  private val project: Project,
  private val getSelectedTemplate: () -> SdkIntegrationWizard.PaywallTemplate?,
  private val onComplete: () -> Unit,
) : JBPanel<JBPanel<*>>() {

  private val addDependencyCheckbox =
    JBCheckBox("Add purchases-ui dependency to build.gradle.kts", true)
  private val generateCodeCheckbox = JBCheckBox("Generate paywall code template", true)
  private val versionLabel = JBLabel()
  private lateinit var codePreviewTextArea: JTextArea

  private var sdkVersion = "2.2.11+17.21.0" // Default fallback - same version as purchases-kmp SDK

  init {
    layout = BorderLayout()
    border = JBUI.Borders.empty(20)

    setupUI()
    fetchLatestVersion()
  }

  private fun fetchLatestVersion() {
    versionLabel.text = "<html><small>Fetching latest version...</small></html>"
    Thread {
      try {
        // purchases-kmp-ui uses the same version as purchases-kmp
        sdkVersion = GitHubVersionService.getLatestKmpVersion()
        SwingUtilities.invokeLater {
          versionLabel.text = "<html><small>Latest version: <b>$sdkVersion</b></small></html>"
          codePreviewTextArea.text = getCodePreview()
        }
      } catch (e: Exception) {
        SwingUtilities.invokeLater {
          versionLabel.text = "<html><small>Using default version: <b>$sdkVersion</b></small></html>"
        }
      }
    }.start()
  }

  private fun setupUI() {
    val mainPanel = JBPanel<JBPanel<*>>()
    mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

    // Title
    val titleLabel = JBLabel("<html><h1>Kotlin Multiplatform Paywall Setup</h1></html>")
    titleLabel.alignmentX = 0.0f
    mainPanel.add(titleLabel)
    mainPanel.add(Box.createVerticalStrut(10))

    // Description
    val descLabel =
      JBLabel(
        "<html>Configure RevenueCat Paywall UI for your KMP project using Compose Multiplatform.</html>",
      )
    descLabel.alignmentX = 0.0f
    mainPanel.add(descLabel)
    mainPanel.add(Box.createVerticalStrut(20))

    // Options section
    mainPanel.add(createOptionsSection())
    mainPanel.add(Box.createVerticalStrut(20))

    // Code preview section
    mainPanel.add(createCodePreviewSection())

    val scrollPane = JBScrollPane(mainPanel)
    scrollPane.border = JBUI.Borders.empty()
    add(scrollPane, BorderLayout.CENTER)

    // Apply button
    val buttonPanel = JBPanel<JBPanel<*>>()
    val applyButton = JButton("Apply Changes")
    applyButton.putClientProperty("JButton.buttonType", "default")
    applyButton.addActionListener {
      applyConfiguration()
    }
    buttonPanel.add(applyButton)
    add(buttonPanel, BorderLayout.SOUTH)
  }

  private fun createOptionsSection(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.alignmentX = 0.0f

    val label = JBLabel("<html><b>What should we do?</b></html>")
    label.alignmentX = 0.0f
    panel.add(label)
    panel.add(Box.createVerticalStrut(10))

    addDependencyCheckbox.alignmentX = 0.0f
    panel.add(addDependencyCheckbox)
    panel.add(Box.createVerticalStrut(5))

    versionLabel.alignmentX = 0.0f
    versionLabel.border = JBUI.Borders.emptyLeft(20)
    panel.add(versionLabel)
    panel.add(Box.createVerticalStrut(5))

    generateCodeCheckbox.alignmentX = 0.0f
    panel.add(generateCodeCheckbox)

    return panel
  }

  private fun createCodePreviewSection(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.alignmentX = 0.0f

    val label = JBLabel("<html><b>Generated Code Preview:</b></html>")
    label.alignmentX = 0.0f
    panel.add(label)
    panel.add(Box.createVerticalStrut(10))

    codePreviewTextArea = JTextArea()
    codePreviewTextArea.isEditable = false
    codePreviewTextArea.lineWrap = true
    codePreviewTextArea.wrapStyleWord = true
    codePreviewTextArea.font = Font("Monospaced", Font.PLAIN, 12)
    codePreviewTextArea.text = getCodePreview()

    val scrollPane = JBScrollPane(codePreviewTextArea)
    scrollPane.preferredSize = java.awt.Dimension(600, 150)
    scrollPane.alignmentX = 0.0f
    panel.add(scrollPane)

    return panel
  }

  private fun getCodePreview(): String {
    return """
      // Add to your shared/build.gradle.kts
      kotlin {
          sourceSets {
              commonMain.dependencies {
                  implementation("com.revenuecat.purchases:purchases-kmp:$sdkVersion")
                  implementation("com.revenuecat.purchases:purchases-kmp-ui:$sdkVersion")
              }
          }
      }

      // Full Screen Paywall Composable (commonMain)
      @Composable
      fun PaywallScreen(
          onDismiss: () -> Unit,
          onPurchaseCompleted: (CustomerInfo) -> Unit
      ) {
          Paywall(
              options = PaywallOptions(
                  dismissRequest = onDismiss
              ),
              listener = object : PaywallListener {
                  override fun onPurchaseCompleted(customerInfo: CustomerInfo) {
                      onPurchaseCompleted(customerInfo)
                  }
              }
          )
      }
    """.trimIndent()
  }

  private fun applyConfiguration() {
    ApplicationManager.getApplication().invokeLater {
      val results = mutableListOf<String>()

      try {
        if (addDependencyCheckbox.isSelected) {
          val dependencyResult = addDependencyToGradle()
          results.add(dependencyResult)
        }

        if (generateCodeCheckbox.isSelected) {
          showGeneratedCode()
          results.add("Generated paywall code template")
        }

        val resultMessage = if (results.isNotEmpty()) {
          results.joinToString("\n• ", "Successfully applied:\n• ")
        } else {
          "No changes were applied"
        }

        Messages.showInfoMessage(
          "$resultMessage\n\n" +
            "Next steps:\n" +
            "1. Sync your Gradle files\n" +
            "2. Add the paywall composable to your shared module\n" +
            "3. Configure your paywall in the RevenueCat dashboard",
          "Configuration Complete",
        )

        onComplete()
      } catch (e: Exception) {
        Messages.showErrorDialog(
          "Error during configuration:\n\n${e.message}",
          "Configuration Error",
        )
        e.printStackTrace()
      }
    }
  }

  private fun addDependencyToGradle(): String {
    val tomlFile = findTomlFile()

    return if (tomlFile != null) {
      addDependencyViaToml(tomlFile)
    } else {
      addDependencyDirectly()
    }
  }

  private fun findTomlFile(): VirtualFile? {
    val gradleDir = project.basePath?.let {
      com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath("$it/gradle")
    }
    return gradleDir?.findChild("libs.versions.toml")
  }

  private fun addDependencyViaToml(tomlFile: VirtualFile): String {
    var modified = false

    WriteCommandAction.runWriteCommandAction(project) {
      val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
        .getDocument(tomlFile)

      if (document != null) {
        val text = document.text

        if (!text.contains("purchases-kmp-ui")) {
          val librariesIndex = text.indexOf("[libraries]")
          if (librariesIndex != -1) {
            val insertIndex = text.indexOf("\n", librariesIndex) + 1
            val libraryEntry = "purchases-kmp-ui = { module = \"com.revenuecat.purchases:purchases-kmp-ui\", version = \"$sdkVersion\" }\n"
            document.insertString(insertIndex, libraryEntry)
            modified = true
          }
        }
      }
    }

    if (modified) {
      addTomlReferenceToSharedGradle()
    }

    return if (modified) {
      "Added purchases-kmp-ui dependency to libs.versions.toml and shared/build.gradle.kts"
    } else {
      "Dependency already exists in TOML"
    }
  }

  private fun addTomlReferenceToSharedGradle() {
    val buildGradleFiles = findBuildGradleFiles()
    val sharedBuildGradle = buildGradleFiles.firstOrNull {
      it.parent?.name == "shared" || it.parent?.name == "composeApp"
    }

    if (sharedBuildGradle != null) {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
          .getDocument(sharedBuildGradle)

        if (document != null) {
          val text = document.text

          if (!text.contains("purchases-kmp-ui") && !text.contains("purchases.kmp.ui")) {
            // Find commonMain.dependencies block
            val commonMainIndex = text.indexOf("commonMain")
            if (commonMainIndex != -1) {
              val dependenciesIndex = text.indexOf("dependencies", commonMainIndex)
              if (dependenciesIndex != -1) {
                val braceIndex = text.indexOf("{", dependenciesIndex)
                if (braceIndex != -1) {
                  val insertIndex = braceIndex + 1
                  val dependency = "\n                    implementation(libs.purchases.kmp.ui)"
                  document.insertString(insertIndex, dependency)
                }
              }
            }
          }
        }
      }
    }
  }

  private fun addDependencyDirectly(): String {
    val buildGradleFiles = findBuildGradleFiles()
    val sharedBuildGradle = buildGradleFiles.firstOrNull {
      it.parent?.name == "shared" || it.parent?.name == "composeApp"
    }

    if (sharedBuildGradle == null) {
      return "Could not find shared/build.gradle.kts or composeApp/build.gradle.kts"
    }

    var modified = false
    WriteCommandAction.runWriteCommandAction(project) {
      val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
        .getDocument(sharedBuildGradle)

      if (document != null) {
        val text = document.text

        if (!text.contains("purchases-kmp-ui")) {
          val commonMainIndex = text.indexOf("commonMain")
          if (commonMainIndex != -1) {
            val dependenciesIndex = text.indexOf("dependencies", commonMainIndex)
            if (dependenciesIndex != -1) {
              val braceIndex = text.indexOf("{", dependenciesIndex)
              if (braceIndex != -1) {
                val insertIndex = braceIndex + 1
                val dependency = "\n                    implementation(\"com.revenuecat.purchases:purchases-kmp-ui:$sdkVersion\")"
                document.insertString(insertIndex, dependency)
                modified = true
              }
            }
          }
        }
      }
    }

    return if (modified) {
      "Added purchases-kmp-ui dependency to shared/build.gradle.kts"
    } else {
      "Dependency already exists"
    }
  }

  private fun findBuildGradleFiles(): List<VirtualFile> {
    val results = mutableListOf<VirtualFile>()

    ApplicationManager.getApplication().runReadAction {
      FilenameIndex.getFilesByName(
        project,
        "build.gradle.kts",
        GlobalSearchScope.projectScope(project),
      ).forEach { psiFile ->
        results.add(psiFile.virtualFile)
      }
    }

    return results
  }

  private fun showGeneratedCode() {
    val code = generateFullCode()

    ApplicationManager.getApplication().invokeLater {
      showCodeDialog("KMP Full Screen Paywall Code", code)
    }
  }

  private fun generateFullCode(): String {
    return """
      package com.yourapp.ui.paywall

      import androidx.compose.runtime.Composable
      import com.revenuecat.purchases.kmp.CustomerInfo
      import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
      import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallListener
      import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions

      /**
       * Full-screen paywall presentation for Compose Multiplatform
       */
      @Composable
      fun PaywallScreen(
          onDismiss: () -> Unit,
          onPurchaseCompleted: (CustomerInfo) -> Unit = {},
          onPurchaseError: (Exception) -> Unit = {},
          onRestoreCompleted: (CustomerInfo) -> Unit = {}
      ) {
          Paywall(
              options = PaywallOptions(
                  dismissRequest = onDismiss
              ),
              listener = object : PaywallListener {
                  override fun onPurchaseCompleted(customerInfo: CustomerInfo) {
                      onPurchaseCompleted(customerInfo)
                  }

                  override fun onPurchaseError(error: Exception) {
                      onPurchaseError(error)
                  }

                  override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                      onRestoreCompleted(customerInfo)
                  }
              }
          )
      }

      // Usage in your shared Compose code:
      //
      // @Composable
      // fun App() {
      //     var showPaywall by remember { mutableStateOf(false) }
      //
      //     if (showPaywall) {
      //         PaywallScreen(
      //             onDismiss = { showPaywall = false },
      //             onPurchaseCompleted = { customerInfo ->
      //                 showPaywall = false
      //                 // Handle successful purchase
      //             }
      //         )
      //     }
      // }
    """.trimIndent()
  }

  private fun showCodeDialog(title: String, code: String) {
    val dialog = com.intellij.openapi.ui.DialogBuilder(project)
    dialog.setTitle(title)

    val panel = JBPanel<JBPanel<*>>(BorderLayout())
    panel.border = JBUI.Borders.empty(10)

    val textArea = JTextArea(code)
    textArea.isEditable = false
    textArea.font = Font("Monospaced", Font.PLAIN, 12)
    textArea.background = com.intellij.ui.JBColor(
      java.awt.Color(245, 245, 245),
      java.awt.Color(43, 43, 43),
    )
    textArea.border = JBUI.Borders.empty(10)

    val scrollPane = JBScrollPane(textArea)
    scrollPane.preferredSize = java.awt.Dimension(700, 500)

    panel.add(scrollPane, BorderLayout.CENTER)

    val copyButton = JButton("Copy to Clipboard")
    copyButton.addActionListener {
      val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
      clipboard.setContents(java.awt.datatransfer.StringSelection(code), null)
      copyButton.text = "Copied!"
      Timer(2000) { copyButton.text = "Copy to Clipboard" }.apply {
        isRepeats = false
        start()
      }
    }

    val buttonPanel = JBPanel<JBPanel<*>>()
    buttonPanel.add(copyButton)
    panel.add(buttonPanel, BorderLayout.SOUTH)

    dialog.setCenterPanel(panel)
    dialog.addOkAction()
    dialog.show()
  }
}
