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
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.revenuecat.plugin.services.GitHubVersionService
import com.revenuecat.plugin.settings.RevenueCatSettingsState
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
 * Configuration panel for Kotlin Multiplatform SDK integration
 */
class KotlinConfigurationPanel(
  private val project: Project,
  private val onComplete: () -> Unit,
) : JBPanel<JBPanel<*>>() {

  private val androidApiKeyField = JBTextField()
  private val iosApiKeyField = JBTextField()
  private val addDependencyCheckbox =
    JBCheckBox("Add RevenueCat KMP dependency to build.gradle.kts", true)
  private val generateCodeCheckbox = JBCheckBox("Generate initialization code", true)
  private val versionLabel = JBLabel()

  private var revenueCatVersion = "2.2.11+17.21.0" // Default fallback

  init {
    layout = BorderLayout()
    border = JBUI.Borders.empty(20)

    setupUI()
    loadApiKeys()
    fetchLatestVersion()
  }

  private fun fetchLatestVersion() {
    versionLabel.text = "<html><small>Fetching latest version...</small></html>"
    Thread {
      try {
        revenueCatVersion = GitHubVersionService.getLatestKmpVersion()
        SwingUtilities.invokeLater {
          versionLabel.text = "<html><small>Latest version: <b>$revenueCatVersion</b></small></html>"
        }
      } catch (e: Exception) {
        SwingUtilities.invokeLater {
          versionLabel.text = "<html><small>Using default version: <b>$revenueCatVersion</b></small></html>"
        }
      }
    }.start()
  }

  private fun setupUI() {
    val mainPanel = JBPanel<JBPanel<*>>()
    mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

    // Title
    val titleLabel = JBLabel("<html><h1>Kotlin Multiplatform SDK Configuration</h1></html>")
    titleLabel.alignmentX = 0.0f
    mainPanel.add(titleLabel)
    mainPanel.add(Box.createVerticalStrut(20))

    // API Keys section
    mainPanel.add(createApiKeysSection())
    mainPanel.add(Box.createVerticalStrut(20))

    // Options section
    mainPanel.add(createOptionsSection())
    mainPanel.add(Box.createVerticalStrut(20))

    // Instructions section
    mainPanel.add(createInstructionsSection())

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

  private fun createApiKeysSection(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.alignmentX = 0.0f

    // Android API Key
    val androidLabel = JBLabel("<html><b>Google Play API Key</b></html>")
    androidLabel.alignmentX = 0.0f
    panel.add(androidLabel)
    panel.add(Box.createVerticalStrut(5))

    val androidHelpLabel =
      JBLabel("<html><small>Your Android SDK API Key (starts with 'goog_')</small></html>")
    androidHelpLabel.alignmentX = 0.0f
    panel.add(androidHelpLabel)
    panel.add(Box.createVerticalStrut(10))

    androidApiKeyField.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 30)
    androidApiKeyField.alignmentX = 0.0f
    panel.add(androidApiKeyField)
    panel.add(Box.createVerticalStrut(20))

    // iOS API Key
    val iosLabel = JBLabel("<html><b>App Store API Key</b></html>")
    iosLabel.alignmentX = 0.0f
    panel.add(iosLabel)
    panel.add(Box.createVerticalStrut(5))

    val iosHelpLabel =
      JBLabel("<html><small>Your iOS SDK API Key (starts with 'appl_')</small></html>")
    iosHelpLabel.alignmentX = 0.0f
    panel.add(iosHelpLabel)
    panel.add(Box.createVerticalStrut(10))

    iosApiKeyField.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 30)
    iosApiKeyField.alignmentX = 0.0f
    panel.add(iosApiKeyField)

    return panel
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

  private fun createInstructionsSection(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.alignmentX = 0.0f

    val label = JBLabel("<html><b>What will be added:</b></html>")
    label.alignmentX = 0.0f
    panel.add(label)
    panel.add(Box.createVerticalStrut(10))

    val instructions = JTextArea()
    instructions.isEditable = false
    instructions.lineWrap = true
    instructions.wrapStyleWord = true
    instructions.font = Font("Monospaced", Font.PLAIN, 12)
    instructions.text = """
      1. Dependency (latest version from GitHub) in commonMain:
         dependencies {
             implementation("com.revenuecat.purchases:purchases-kmp:<version>")
         }

      2. Initialization code:
         // Android
         Purchases.configure(
             PurchasesConfiguration("goog_YOUR_API_KEY") {
                 appUserId = null
             }
         )

         // iOS
         Purchases.configure(
             PurchasesConfiguration("appl_YOUR_API_KEY") {
                 appUserId = null
             }
         )
    """.trimIndent()

    val scrollPane = JBScrollPane(instructions)
    scrollPane.preferredSize = java.awt.Dimension(600, 250)
    scrollPane.alignmentX = 0.0f
    panel.add(scrollPane)

    return panel
  }

  private fun loadApiKeys() {
    val settings = RevenueCatSettingsState.getInstance()
    if (settings.sdkApiKey.isNotBlank()) {
      // Try to detect which platform based on prefix
      when {
        settings.sdkApiKey.startsWith("goog_") -> androidApiKeyField.text = settings.sdkApiKey
        settings.sdkApiKey.startsWith("appl_") -> iosApiKeyField.text = settings.sdkApiKey
        else -> androidApiKeyField.text = settings.sdkApiKey
      }
    }
  }

  private fun applyConfiguration() {
    val androidKey = androidApiKeyField.text.trim()
    val iosKey = iosApiKeyField.text.trim()

    if (androidKey.isBlank() && iosKey.isBlank()) {
      Messages.showErrorDialog(
        "Please enter at least one API key (Android or iOS)",
        "API Key Required",
      )
      return
    }

    if (androidKey.isNotBlank() && !androidKey.startsWith("goog_")) {
      Messages.showWarningDialog(
        "Android API Key should start with 'goog_'. Please verify you're using the correct key.",
        "Invalid API Key Format",
      )
      return
    }

    if (iosKey.isNotBlank() && !iosKey.startsWith("appl_")) {
      Messages.showWarningDialog(
        "iOS API Key should start with 'appl_'. Please verify you're using the correct key.",
        "Invalid API Key Format",
      )
      return
    }

    ApplicationManager.getApplication().invokeLater {
      val results = mutableListOf<String>()

      try {
        if (addDependencyCheckbox.isSelected) {
          val dependencyResult = addDependencyToGradle()
          results.add(dependencyResult)
        }

        if (generateCodeCheckbox.isSelected) {
          generateInitializationCode(androidKey, iosKey)
          results.add("Generated initialization code")
        }

        // Save API key to settings
        val settings = RevenueCatSettingsState.getInstance()
        settings.sdkApiKey = androidKey.ifBlank { iosKey }

        val resultMessage = results.joinToString("\n• ", "Successfully applied:\n• ")

        Messages.showInfoMessage(
          "$resultMessage\n\n" +
            "Next steps:\n" +
            "1. Sync your Gradle files\n" +
            "2. Review the generated code\n" +
            "3. Start making purchases!",
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

  private fun generateInitializationCode(androidKey: String, iosKey: String) {
    val code = StringBuilder()
    code.appendLine("// Add this code to configure RevenueCat in your shared module")
    code.appendLine()
    code.appendLine("import com.revenuecat.purchases.kmp.Purchases")
    code.appendLine("import com.revenuecat.purchases.kmp.PurchasesConfiguration")
    code.appendLine()

    if (androidKey.isNotBlank() && iosKey.isNotBlank()) {
      code.appendLine("// Configure with platform-specific API keys")
      code.appendLine("expect fun getPlatformApiKey(): String")
      code.appendLine()
      code.appendLine("fun configurePurchases() {")
      code.appendLine("    Purchases.configure(")
      code.appendLine("        PurchasesConfiguration(getPlatformApiKey()) {")
      code.appendLine("            appUserId = null")
      code.appendLine("        }")
      code.appendLine("    )")
      code.appendLine("}")
      code.appendLine()
      code.appendLine("// androidMain")
      code.appendLine("actual fun getPlatformApiKey() = \"$androidKey\"")
      code.appendLine()
      code.appendLine("// iosMain")
      code.appendLine("actual fun getPlatformApiKey() = \"$iosKey\"")
    } else {
      val key = androidKey.ifBlank { iosKey }
      code.appendLine("fun configurePurchases() {")
      code.appendLine("    Purchases.configure(")
      code.appendLine("        PurchasesConfiguration(\"$key\") {")
      code.appendLine("            appUserId = null")
      code.appendLine("        }")
      code.appendLine("    )")
      code.appendLine("}")
    }

    // Show code in a dialog with code box
    ApplicationManager.getApplication().invokeLater {
      showCodeDialog("Initialization Code (Kotlin Multiplatform)", code.toString())
    }
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
    scrollPane.preferredSize = java.awt.Dimension(600, 400)

    panel.add(scrollPane, BorderLayout.CENTER)

    // Add copy button
    val copyButton = JButton("Copy to Clipboard")
    copyButton.addActionListener {
      val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
      clipboard.setContents(java.awt.datatransfer.StringSelection(code), null)
      copyButton.text = "Copied!"
      javax.swing.Timer(2000) { copyButton.text = "Copy to Clipboard" }.apply {
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

  private fun addDependencyToGradle(): String {
    // First, check if libs.versions.toml exists
    val tomlFile = findTomlFile()

    return if (tomlFile != null) {
      addDependencyViaToml(tomlFile)
    } else {
      addDependencyDirectly()
    }
  }

  private fun findTomlFile(): VirtualFile? {
    val gradleDir = project.baseDir?.findChild("gradle")
    return gradleDir?.findChild("libs.versions.toml")
  }

  private fun addDependencyViaToml(tomlFile: VirtualFile): String {
    var modified = false

    WriteCommandAction.runWriteCommandAction(project) {
      val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
        .getDocument(tomlFile)

      if (document != null) {
        val text = document.text

        // Check if already exists
        if (text.contains(
            "purchases-kmp",
          ) || text.contains("com.revenuecat.purchases:purchases-kmp")
        ) {
          return@runWriteCommandAction
        }

        // Add to [libraries] section
        val librariesIndex = text.indexOf("[libraries]")
        if (librariesIndex != -1) {
          val insertIndex = text.indexOf("\n", librariesIndex) + 1
          val libraryEntry = "purchases-kmp = { module = \"com.revenuecat.purchases:purchases-kmp\", version = \"$revenueCatVersion\" }\n"
          document.insertString(insertIndex, libraryEntry)
          modified = true
        }
      }
    }

    // Now add to shared/build.gradle.kts commonMain
    if (modified) {
      addTomlReferenceToSharedGradle()
    }

    return if (modified) {
      "Added dependency to libs.versions.toml and shared module build.gradle.kts"
    } else {
      "Dependency already exists in TOML"
    }
  }

  private fun addTomlReferenceToSharedGradle() {
    val buildGradleFiles = findBuildGradleKtsFiles()
    // Look for shared module or composeApp build.gradle.kts (common KMP module names)
    val sharedBuildGradle = buildGradleFiles.firstOrNull {
      it.parent?.name == "shared" || it.parent?.name == "composeApp" || it.parent?.name == "commonMain"
    } ?: buildGradleFiles.firstOrNull {
      // Check if it contains commonMain sourceSets
      val content = try {
        String(it.contentsToByteArray())
      } catch (e: Exception) {
        ""
      }
      content.contains("commonMain")
    }

    if (sharedBuildGradle != null) {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
          .getDocument(sharedBuildGradle)

        if (document != null) {
          val text = document.text

          // Check if dependency already exists
          if (!text.contains(
              "purchases-kmp",
            ) && !text.contains("com.revenuecat.purchases:purchases-kmp")
          ) {
            // Find commonMain dependencies block
            val commonMainIndex = text.indexOf("commonMain")
            if (commonMainIndex != -1) {
              // Look for dependencies block after commonMain
              val afterCommonMain = text.substring(commonMainIndex)
              val dependenciesIndex = afterCommonMain.indexOf("dependencies {")
              if (dependenciesIndex != -1) {
                val insertIndex = commonMainIndex + dependenciesIndex + "dependencies {".length
                val dependency = "\n                implementation(libs.purchases.kmp)"
                document.insertString(insertIndex, dependency)
              }
            }
          }
        }
      }
    }
  }

  private fun addDependencyDirectly(): String {
    val buildGradleFiles = findBuildGradleKtsFiles()
    // Look for shared module or composeApp build.gradle.kts
    val sharedBuildGradle = buildGradleFiles.firstOrNull {
      it.parent?.name == "shared" || it.parent?.name == "composeApp" || it.parent?.name == "commonMain"
    } ?: buildGradleFiles.firstOrNull {
      // Check if it contains commonMain sourceSets
      val content = try {
        String(it.contentsToByteArray())
      } catch (e: Exception) {
        ""
      }
      content.contains("commonMain")
    }

    if (sharedBuildGradle == null) {
      return "Could not find KMP shared module build.gradle.kts"
    }

    var modified = false
    WriteCommandAction.runWriteCommandAction(project) {
      val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
        .getDocument(sharedBuildGradle)

      if (document != null) {
        val text = document.text

        // Check if dependency already exists
        if (!text.contains("com.revenuecat.purchases:purchases-kmp")) {
          // Find commonMain dependencies block
          val commonMainIndex = text.indexOf("commonMain")
          if (commonMainIndex != -1) {
            // Look for dependencies block after commonMain
            val afterCommonMain = text.substring(commonMainIndex)
            val dependenciesIndex = afterCommonMain.indexOf("dependencies {")
            if (dependenciesIndex != -1) {
              val insertIndex = commonMainIndex + dependenciesIndex + "dependencies {".length
              val dependency = "\n                implementation(\"com.revenuecat.purchases:purchases-kmp:$revenueCatVersion\")"
              document.insertString(insertIndex, dependency)
              modified = true
            }
          }
        }
      }
    }

    return if (modified) {
      "Added dependency to shared module build.gradle.kts"
    } else {
      "Dependency already exists or commonMain not found"
    }
  }

  private fun findBuildGradleKtsFiles(): List<VirtualFile> {
    val results = mutableListOf<VirtualFile>()

    // Search for build.gradle.kts files
    FilenameIndex.getFilesByName(
      project,
      "build.gradle.kts",
      GlobalSearchScope.projectScope(project),
    ).forEach { psiFile ->
      results.add(psiFile.virtualFile)
    }

    return results
  }
}
