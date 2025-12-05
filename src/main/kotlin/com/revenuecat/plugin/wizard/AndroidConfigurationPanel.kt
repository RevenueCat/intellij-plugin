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
 * Configuration panel for Android SDK integration
 */
class AndroidConfigurationPanel(
  private val project: Project,
  private val onComplete: () -> Unit,
) : JBPanel<JBPanel<*>>() {

  private val apiKeyField = JBTextField()
  private val addDependencyCheckbox = JBCheckBox("Add RevenueCat dependency to build.gradle", true)
  private val injectConfigureCheckbox = JBCheckBox("Generate initialization code", true)
  private val versionLabel = JBLabel()
  private val applicationClassLabel = JBLabel()

  private var revenueCatVersion = "9.15.1" // Default fallback

  private var detectedApplicationClass: ApplicationClassInfo? = null

  init {
    layout = BorderLayout()
    border = JBUI.Borders.empty(20)

    setupUI()
    loadApiKey()
    fetchLatestVersion()
    detectApplicationClass()
  }

  data class ApplicationClassInfo(
    val className: String,
    val packageName: String,
    val file: VirtualFile,
  ) {
    val fullyQualifiedName: String get() = "$packageName.$className"
  }

  private fun fetchLatestVersion() {
    versionLabel.text = "<html><small>Fetching latest version...</small></html>"
    Thread {
      try {
        revenueCatVersion = GitHubVersionService.getLatestAndroidVersion()
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
    val titleLabel = JBLabel("<html><h1>Android SDK Configuration</h1></html>")
    titleLabel.alignmentX = 0.0f
    mainPanel.add(titleLabel)
    mainPanel.add(Box.createVerticalStrut(20))

    // API Key section
    mainPanel.add(createApiKeySection())
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

  private fun createApiKeySection(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.alignmentX = 0.0f

    val label = JBLabel("<html><b>Google Play API Key</b></html>")
    label.alignmentX = 0.0f
    panel.add(label)
    panel.add(Box.createVerticalStrut(5))

    val helpLabel =
      JBLabel(
        "<html><small>Your SDK API Key from RevenueCat dashboard (starts with 'goog_')</small></html>",
      )
    helpLabel.alignmentX = 0.0f
    panel.add(helpLabel)
    panel.add(Box.createVerticalStrut(10))

    apiKeyField.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 30)
    apiKeyField.alignmentX = 0.0f
    panel.add(apiKeyField)

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
    panel.add(Box.createVerticalStrut(10))

    injectConfigureCheckbox.alignmentX = 0.0f
    panel.add(injectConfigureCheckbox)
    panel.add(Box.createVerticalStrut(5))

    applicationClassLabel.alignmentX = 0.0f
    applicationClassLabel.border = JBUI.Borders.emptyLeft(20)
    applicationClassLabel.text = "<html><small>Detecting Application class...</small></html>"
    panel.add(applicationClassLabel)

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
      1. Dependency (latest version from GitHub):
         - If libs.versions.toml exists: adds to catalog and app/build.gradle
         - Otherwise: adds directly to app/build.gradle

         dependencies {
             implementation("com.revenuecat.purchases:purchases:<version>")
         }

      2. Initialization code to your Application class:
         Purchases.configure(
             PurchasesConfiguration.Builder(this, "YOUR_API_KEY")
                 .build()
         )
    """.trimIndent()

    val scrollPane = JBScrollPane(instructions)
    scrollPane.preferredSize = java.awt.Dimension(600, 250)
    scrollPane.alignmentX = 0.0f
    panel.add(scrollPane)

    return panel
  }

  private fun loadApiKey() {
    val settings = RevenueCatSettingsState.getInstance()
    if (settings.sdkApiKey.isNotBlank()) {
      apiKeyField.text = settings.sdkApiKey
    }
  }

  private fun applyConfiguration() {
    val apiKey = apiKeyField.text.trim()

    if (apiKey.isBlank()) {
      Messages.showErrorDialog(
        "Please enter your Google Play API Key",
        "API Key Required",
      )
      return
    }

    if (!apiKey.startsWith("goog_")) {
      Messages.showWarningDialog(
        "API Key should start with 'goog_'. Please verify you're using the correct key.",
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

        if (injectConfigureCheckbox.isSelected && detectedApplicationClass != null) {
          val injected = injectPurchasesConfigure(apiKey)
          if (injected) {
            results.add(
              "Injected Purchases.configure() into ${detectedApplicationClass!!.className}",
            )
          } else {
            results.add("Could not inject code (already configured or no onCreate method found)")
          }
        }

        // Save API key to settings
        val settings = RevenueCatSettingsState.getInstance()
        settings.sdkApiKey = apiKey

        val resultMessage = results.joinToString("\n• ", "Successfully applied:\n• ")

        // Always show the initialization code
        generateInitializationCode(apiKey)

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
            "purchases-android",
          ) || text.contains("com.revenuecat.purchases:purchases")
        ) {
          return@runWriteCommandAction
        }

        // Add to [libraries] section
        val librariesIndex = text.indexOf("[libraries]")
        if (librariesIndex != -1) {
          val insertIndex = text.indexOf("\n", librariesIndex) + 1
          val libraryEntry = "purchases-android = { module = \"com.revenuecat.purchases:purchases\", version = \"$revenueCatVersion\" }\n"
          document.insertString(insertIndex, libraryEntry)
          modified = true
        }
      }
    }

    // Now add to app/build.gradle
    if (modified) {
      addTomlReferenceToAppGradle()
    }

    return if (modified) {
      "Added dependency to libs.versions.toml and app/build.gradle"
    } else {
      "Dependency already exists in TOML"
    }
  }

  private fun addTomlReferenceToAppGradle() {
    val buildGradleFiles = findBuildGradleFiles()
    val appBuildGradle = buildGradleFiles.firstOrNull { it.parent?.name == "app" }

    if (appBuildGradle != null) {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
          .getDocument(appBuildGradle)

        if (document != null) {
          val text = document.text

          // Check if dependency already exists
          if (!text.contains("purchases-android") && !text.contains("com.revenuecat.purchases")) {
            // Find dependencies block
            val dependenciesIndex = text.indexOf("dependencies {")
            if (dependenciesIndex != -1) {
              val insertIndex = text.indexOf("{", dependenciesIndex) + 1
              val dependency = "\n    implementation(libs.purchases.android)"
              document.insertString(insertIndex, dependency)
            }
          }
        }
      }
    }
  }

  private fun addDependencyDirectly(): String {
    val buildGradleFiles = findBuildGradleFiles()
    val appBuildGradle = buildGradleFiles.firstOrNull { it.parent?.name == "app" }

    if (appBuildGradle == null) {
      return "Could not find app/build.gradle"
    }

    var modified = false
    WriteCommandAction.runWriteCommandAction(project) {
      val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
        .getDocument(appBuildGradle)

      if (document != null) {
        val text = document.text

        // Check if dependency already exists
        if (!text.contains("com.revenuecat.purchases")) {
          // Find dependencies block
          val dependenciesIndex = text.indexOf("dependencies {")
          if (dependenciesIndex != -1) {
            val insertIndex = text.indexOf("{", dependenciesIndex) + 1
            val dependency = "\n    implementation(\"com.revenuecat.purchases:purchases:$revenueCatVersion\")"
            document.insertString(insertIndex, dependency)
            modified = true
          }
        }
      }
    }

    return if (modified) {
      "Added dependency to app/build.gradle"
    } else {
      "Dependency already exists"
    }
  }

  private fun findBuildGradleFiles(): List<VirtualFile> {
    val results = mutableListOf<VirtualFile>()

    // Search for build.gradle files
    FilenameIndex.getFilesByName(
      project,
      "build.gradle",
      GlobalSearchScope.projectScope(project),
    ).forEach { psiFile ->
      results.add(psiFile.virtualFile)
    }

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

  private fun generateInitializationCode(apiKey: String) {
    val code = """
      // Add this code to your Application class onCreate() method (Kotlin)

      import com.revenuecat.purchases.Purchases
      import com.revenuecat.purchases.PurchasesConfiguration

      class MyApplication : Application() {
          override fun onCreate() {
              super.onCreate()

              // Initialize RevenueCat
              Purchases.configure(
                  PurchasesConfiguration.Builder(this, "$apiKey")
                      .build()
              )
          }
      }
    """.trimIndent()

    // Show code in a dialog with code box
    ApplicationManager.getApplication().invokeLater {
      showCodeDialog("Initialization Code (Kotlin)", code)
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

  /**
   * Detect the Application class from AndroidManifest.xml
   */
  private fun detectApplicationClass() {
    applicationClassLabel.text = "<html><small>Detecting Application class...</small></html>"

    Thread {
      try {
        val appInfo = findApplicationClass()
        SwingUtilities.invokeLater {
          detectedApplicationClass = appInfo
          if (appInfo != null) {
            applicationClassLabel.text = "<html><small>Found: <b>${appInfo.fullyQualifiedName}</b></small></html>"
            injectConfigureCheckbox.isEnabled = true
          } else {
            applicationClassLabel.text = "<html><small style='color:gray'>No custom Application class found</small></html>"
            injectConfigureCheckbox.isSelected = false
            injectConfigureCheckbox.isEnabled = false
          }
        }
      } catch (e: Exception) {
        SwingUtilities.invokeLater {
          applicationClassLabel.text = "<html><small style='color:gray'>Could not detect Application class</small></html>"
          injectConfigureCheckbox.isSelected = false
          injectConfigureCheckbox.isEnabled = false
        }
      }
    }.start()
  }

  /**
   * Find the Application class by reading AndroidManifest.xml
   */
  private fun findApplicationClass(): ApplicationClassInfo? {
    // Find all AndroidManifest.xml files
    val manifestFiles = mutableListOf<VirtualFile>()

    ApplicationManager.getApplication().runReadAction {
      FilenameIndex.getFilesByName(
        project,
        "AndroidManifest.xml",
        GlobalSearchScope.projectScope(project),
      ).forEach { psiFile ->
        manifestFiles.add(psiFile.virtualFile)
      }
    }

    // Look for the main app manifest (in app/src/main or similar)
    val appManifest = manifestFiles.firstOrNull { file ->
      val path = file.path
      (path.contains("/app/") || path.contains("/main/")) && !path.contains("/build/")
    } ?: manifestFiles.firstOrNull { !it.path.contains("/build/") }

    if (appManifest == null) return null

    // Parse the manifest to find android:name in <application> tag
    val manifestContent = String(appManifest.contentsToByteArray())
    val applicationNameMatch = Regex("""<application[^>]*android:name\s*=\s*["']([^"']+)["']""")
      .find(manifestContent)

    val applicationClassName = applicationNameMatch?.groupValues?.get(1) ?: return null

    // Also extract package name from manifest
    val packageMatch = Regex("""package\s*=\s*["']([^"']+)["']""").find(manifestContent)
    val manifestPackage = packageMatch?.groupValues?.get(1) ?: ""

    // Resolve the full class name
    val fullClassName = if (applicationClassName.startsWith(".")) {
      manifestPackage + applicationClassName
    } else {
      applicationClassName
    }

    // Find the actual Kotlin/Java file
    val className = fullClassName.substringAfterLast(".")
    val packageName = fullClassName.substringBeforeLast(".")

    // Search for the file
    var applicationFile: VirtualFile? = null

    ApplicationManager.getApplication().runReadAction {
      // Try Kotlin first
      FilenameIndex.getFilesByName(
        project,
        "$className.kt",
        GlobalSearchScope.projectScope(project),
      ).forEach { psiFile ->
        if (!psiFile.virtualFile.path.contains("/build/")) {
          applicationFile = psiFile.virtualFile
        }
      }

      // Try Java if Kotlin not found
      if (applicationFile == null) {
        FilenameIndex.getFilesByName(
          project,
          "$className.java",
          GlobalSearchScope.projectScope(project),
        ).forEach { psiFile ->
          if (!psiFile.virtualFile.path.contains("/build/")) {
            applicationFile = psiFile.virtualFile
          }
        }
      }
    }

    return applicationFile?.let {
      ApplicationClassInfo(className, packageName, it)
    }
  }

  /**
   * Inject Purchases.configure() into the Application class
   */
  private fun injectPurchasesConfigure(apiKey: String): Boolean {
    val appInfo = detectedApplicationClass ?: return false
    val file = appInfo.file

    var success = false

    WriteCommandAction.runWriteCommandAction(project) {
      val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
        .getDocument(file)

      if (document != null) {
        val text = document.text

        // Check if already configured
        if (text.contains("Purchases.configure") || text.contains("PurchasesConfiguration")) {
          return@runWriteCommandAction
        }

        val isKotlin = file.extension == "kt"

        if (isKotlin) {
          success = injectKotlinConfigure(document, text, apiKey)
        } else {
          success = injectJavaConfigure(document, text, apiKey)
        }
      }
    }

    return success
  }

  private fun injectKotlinConfigure(
    document: com.intellij.openapi.editor.Document,
    text: String,
    apiKey: String,
  ): Boolean {
    // Find the onCreate method
    val onCreateMatch = Regex("""override\s+fun\s+onCreate\s*\(\s*\)\s*\{""").find(text)
      ?: return false

    val insertPosition = onCreateMatch.range.last + 1

    // Check if there's a super.onCreate() call
    val superCallMatch = Regex("""super\.onCreate\s*\(\s*\)""").find(text, insertPosition)

    val configureCode = """

        // Initialize RevenueCat
        Purchases.configure(
            PurchasesConfiguration.Builder(this, "$apiKey")
                .build()
        )"""

    val actualInsertPosition = if (superCallMatch != null && superCallMatch.range.first < text.indexOf(
        "}",
        insertPosition,
      )
    ) {
      // Insert after super.onCreate()
      superCallMatch.range.last + 1
    } else {
      insertPosition
    }

    document.insertString(actualInsertPosition, configureCode)

    // Add imports if needed
    addKotlinImports(document)

    return true
  }

  private fun injectJavaConfigure(
    document: com.intellij.openapi.editor.Document,
    text: String,
    apiKey: String,
  ): Boolean {
    // Find the onCreate method
    val onCreateMatch = Regex("""public\s+void\s+onCreate\s*\(\s*\)\s*\{""").find(text)
      ?: Regex("""void\s+onCreate\s*\(\s*\)\s*\{""").find(text)
      ?: return false

    val insertPosition = onCreateMatch.range.last + 1

    // Check if there's a super.onCreate() call
    val superCallMatch = Regex("""super\.onCreate\s*\(\s*\)\s*;""").find(text, insertPosition)

    val configureCode = """

        // Initialize RevenueCat
        Purchases.configure(
            new PurchasesConfiguration.Builder(this, "$apiKey")
                .build()
        );"""

    val actualInsertPosition = if (superCallMatch != null && superCallMatch.range.first < text.indexOf(
        "}",
        insertPosition,
      )
    ) {
      superCallMatch.range.last + 1
    } else {
      insertPosition
    }

    document.insertString(actualInsertPosition, configureCode)

    // Add imports if needed
    addJavaImports(document)

    return true
  }

  private fun addKotlinImports(document: com.intellij.openapi.editor.Document) {
    val text = document.text
    val importsToAdd = mutableListOf<String>()

    if (!text.contains("import com.revenuecat.purchases.Purchases")) {
      importsToAdd.add("import com.revenuecat.purchases.Purchases")
    }
    if (!text.contains("import com.revenuecat.purchases.PurchasesConfiguration")) {
      importsToAdd.add("import com.revenuecat.purchases.PurchasesConfiguration")
    }

    if (importsToAdd.isNotEmpty()) {
      // Find the last import statement or package statement
      val lastImportMatch = Regex("""import\s+[\w.]+(\.\*)?""").findAll(text).lastOrNull()
      val packageMatch = Regex("""package\s+[\w.]+""").find(text)

      val insertPosition = when {
        lastImportMatch != null -> lastImportMatch.range.last + 1
        packageMatch != null -> packageMatch.range.last + 1
        else -> 0
      }

      val importsText = "\n" + importsToAdd.joinToString("\n")
      document.insertString(insertPosition, importsText)
    }
  }

  private fun addJavaImports(document: com.intellij.openapi.editor.Document) {
    val text = document.text
    val importsToAdd = mutableListOf<String>()

    if (!text.contains("import com.revenuecat.purchases.Purchases;")) {
      importsToAdd.add("import com.revenuecat.purchases.Purchases;")
    }
    if (!text.contains("import com.revenuecat.purchases.PurchasesConfiguration;")) {
      importsToAdd.add("import com.revenuecat.purchases.PurchasesConfiguration;")
    }

    if (importsToAdd.isNotEmpty()) {
      val lastImportMatch = Regex("""import\s+[\w.]+(\.\*)?;""").findAll(text).lastOrNull()
      val packageMatch = Regex("""package\s+[\w.]+;""").find(text)

      val insertPosition = when {
        lastImportMatch != null -> lastImportMatch.range.last + 1
        packageMatch != null -> packageMatch.range.last + 1
        else -> 0
      }

      val importsText = "\n" + importsToAdd.joinToString("\n")
      document.insertString(insertPosition, importsText)
    }
  }
}
