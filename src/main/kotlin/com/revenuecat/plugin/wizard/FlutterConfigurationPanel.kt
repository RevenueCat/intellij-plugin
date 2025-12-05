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
 * Configuration panel for Flutter SDK integration
 */
class FlutterConfigurationPanel(
  private val project: Project,
  private val onComplete: () -> Unit,
) : JBPanel<JBPanel<*>>() {

  private val androidApiKeyField = JBTextField()
  private val iosApiKeyField = JBTextField()
  private val addDependencyCheckbox = JBCheckBox("Add purchases_flutter to pubspec.yaml", true)
  private val injectConfigureCheckbox = JBCheckBox("Generate initialization code", true)
  private val versionLabel = JBLabel()
  private val mainDartLabel = JBLabel()

  private var revenueCatVersion = "9.9.9" // Default fallback
  private var detectedMainDartFile: VirtualFile? = null

  init {
    layout = BorderLayout()
    border = JBUI.Borders.empty(20)

    setupUI()
    loadApiKeys()
    fetchLatestVersion()
    detectMainDartFile()
  }

  private fun fetchLatestVersion() {
    versionLabel.text = "<html><small>Fetching latest version...</small></html>"
    Thread {
      try {
        revenueCatVersion = GitHubVersionService.getLatestFlutterVersion()
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
    val titleLabel = JBLabel("<html><h1>Flutter SDK Configuration</h1></html>")
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
    panel.add(Box.createVerticalStrut(10))

    injectConfigureCheckbox.alignmentX = 0.0f
    panel.add(injectConfigureCheckbox)
    panel.add(Box.createVerticalStrut(5))

    mainDartLabel.alignmentX = 0.0f
    mainDartLabel.border = JBUI.Borders.emptyLeft(20)
    mainDartLabel.text = "<html><small>Detecting main.dart...</small></html>"
    panel.add(mainDartLabel)

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
      1. Dependency in pubspec.yaml (latest version from GitHub):
         dependencies:
           purchases_flutter: ^<version>

      2. Initialization code in main.dart:
         import 'package:purchases_flutter/purchases_flutter.dart';

         void main() async {
           WidgetsFlutterBinding.ensureInitialized();

           await Purchases.configure(
             PurchasesConfiguration("YOUR_API_KEY")
               ..appUserID = null
               ..observerMode = false
           );

           runApp(MyApp());
         }

      3. Platform-specific setup:
         - Android: Permissions added automatically
         - iOS: Add StoreKit capability in Xcode
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

  /**
   * Detect the main.dart file in the Flutter project
   */
  private fun detectMainDartFile() {
    mainDartLabel.text = "<html><small>Detecting main.dart...</small></html>"

    Thread {
      try {
        val mainDart = findMainDartFile()
        SwingUtilities.invokeLater {
          detectedMainDartFile = mainDart
          if (mainDart != null) {
            val relativePath = mainDart.path.substringAfter(project.basePath ?: "")
            mainDartLabel.text = "<html><small>Found: <b>$relativePath</b></small></html>"
            injectConfigureCheckbox.isEnabled = true
          } else {
            mainDartLabel.text = "<html><small style='color:gray'>main.dart not found</small></html>"
            injectConfigureCheckbox.isSelected = false
            injectConfigureCheckbox.isEnabled = false
          }
        }
      } catch (e: Exception) {
        SwingUtilities.invokeLater {
          mainDartLabel.text = "<html><small style='color:gray'>Could not detect main.dart</small></html>"
          injectConfigureCheckbox.isSelected = false
          injectConfigureCheckbox.isEnabled = false
        }
      }
    }.start()
  }

  /**
   * Find the main.dart file in the project
   */
  private fun findMainDartFile(): VirtualFile? {
    val mainDartFiles = mutableListOf<VirtualFile>()

    ApplicationManager.getApplication().runReadAction {
      FilenameIndex.getFilesByName(
        project,
        "main.dart",
        GlobalSearchScope.projectScope(project),
      ).forEach { psiFile ->
        // Exclude build directories
        if (!psiFile.virtualFile.path.contains("/build/") &&
          !psiFile.virtualFile.path.contains("/.dart_tool/")
        ) {
          mainDartFiles.add(psiFile.virtualFile)
        }
      }
    }

    // Prefer lib/main.dart
    return mainDartFiles.firstOrNull { it.path.contains("/lib/main.dart") }
      ?: mainDartFiles.firstOrNull()
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
      var injectionFailed = false

      try {
        if (addDependencyCheckbox.isSelected) {
          addDependencyToPubspec()
          results.add("Added purchases_flutter to pubspec.yaml")
        }

        if (injectConfigureCheckbox.isSelected && detectedMainDartFile != null) {
          val injected = injectPurchasesConfigure(androidKey, iosKey)
          if (injected) {
            results.add("Injected Purchases.configure() into main.dart")
          } else {
            results.add("Could not inject code (already configured or no main() function found)")
            injectionFailed = true
          }
        } else if (injectConfigureCheckbox.isSelected && detectedMainDartFile == null) {
          injectionFailed = true
        }

        // Save API key to settings (save Android key if provided, otherwise iOS)
        val settings = RevenueCatSettingsState.getInstance()
        settings.sdkApiKey = androidKey.ifBlank { iosKey }

        // Show the initialization code if injection failed
        if (injectionFailed) {
          generateInitializationCode(androidKey, iosKey)
        }

        val resultMessage = if (results.isNotEmpty()) {
          results.joinToString("\n• ", "Successfully applied:\n• ")
        } else {
          "Configuration saved"
        }

        Messages.showInfoMessage(
          "$resultMessage\n\n" +
            "Next steps:\n" +
            "1. Run 'flutter pub get'\n" +
            "2. Review the generated code\n" +
            "3. Add platform-specific configuration\n" +
            "4. Start making purchases!",
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

  private fun addDependencyToPubspec() {
    // First, find the pubspec file in a read action
    val pubspecFile = ApplicationManager.getApplication().runReadAction<com.intellij.openapi.vfs.VirtualFile?> {
      val pubspecFiles = com.intellij.psi.search.FilenameIndex.getFilesByName(
        project,
        "pubspec.yaml",
        com.intellij.psi.search.GlobalSearchScope.projectScope(project),
      )
      pubspecFiles.firstOrNull()?.virtualFile
    }

    // Then, modify it in a write action (outside of read action)
    if (pubspecFile != null) {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
          .getDocument(pubspecFile)

        if (document != null) {
          val text = document.text

          // Check if dependency already exists
          if (!text.contains("purchases_flutter")) {
            // Find dependencies section
            val dependenciesIndex = text.indexOf("dependencies:")
            if (dependenciesIndex != -1) {
              // Find the end of the line
              val lineEndIndex = text.indexOf("\n", dependenciesIndex) + 1
              val dependency = "  purchases_flutter: ^$revenueCatVersion\n"
              document.insertString(lineEndIndex, dependency)
            }
          }
        }
      }
    }
  }

  private fun generateInitializationCode(androidKey: String, iosKey: String) {
    val code = StringBuilder()
    code.appendLine("// Add this code to your main.dart file")
    code.appendLine()
    code.appendLine("import 'dart:io' show Platform;")
    code.appendLine("import 'package:flutter/material.dart';")
    code.appendLine("import 'package:purchases_flutter/purchases_flutter.dart';")
    code.appendLine()
    code.appendLine("const String _androidApiKey = '$androidKey';")
    code.appendLine("const String _iosApiKey = '$iosKey';")
    code.appendLine()
    code.appendLine("void main() async {")
    code.appendLine("  WidgetsFlutterBinding.ensureInitialized();")
    code.appendLine("  await _initializeRevenueCat();")
    code.appendLine("  runApp(MyApp());")
    code.appendLine("}")
    code.appendLine()
    code.appendLine("Future<void> _initializeRevenueCat() async {")
    code.appendLine("  await Purchases.setLogLevel(LogLevel.debug);")
    code.appendLine()
    code.appendLine("  PurchasesConfiguration? configuration;")
    code.appendLine("  if (Platform.isAndroid) {")
    code.appendLine("    configuration = PurchasesConfiguration(_androidApiKey);")
    code.appendLine("  } else if (Platform.isIOS) {")
    code.appendLine("    configuration = PurchasesConfiguration(_iosApiKey);")
    code.appendLine("  } else {")
    code.appendLine("    print('RevenueCat not configured for this platform.');")
    code.appendLine("    return;")
    code.appendLine("  }")
    code.appendLine()
    code.appendLine("  try {")
    code.appendLine("    await Purchases.configure(configuration);")
    code.appendLine("    print('RevenueCat configured successfully!');")
    code.appendLine("  } catch (e) {")
    code.appendLine("    print('Error configuring RevenueCat: \$e');")
    code.appendLine("  }")
    code.appendLine("}")

    // Show code in a dialog with code box
    ApplicationManager.getApplication().invokeLater {
      showCodeDialog("Initialization Code (Flutter)", code.toString())
    }
  }

  /**
   * Inject Purchases.configure() into main.dart by replacing the entire file content
   */
  private fun injectPurchasesConfigure(androidKey: String, iosKey: String): Boolean {
    val mainDartFile = detectedMainDartFile ?: return false

    var success = false

    try {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
          .getDocument(mainDartFile)

        if (document != null) {
          val text = document.text

          // Check if already configured
          if (text.contains("_initializeRevenueCat")) {
            return@runWriteCommandAction
          }

          // Generate the full main.dart content
          val newContent = generateMainDartContent(androidKey, iosKey)

          // Replace the entire document content
          document.setText(newContent)
          success = true

          // Save the document to disk
          com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(document)
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    return success
  }

  private fun generateMainDartContent(androidKey: String, iosKey: String): String {
    return """
import 'dart:io' show Platform;
import 'package:flutter/material.dart';
import 'package:purchases_flutter/purchases_flutter.dart';

const String _androidApiKey = '$androidKey';
const String _iosApiKey = '$iosKey';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await _initializeRevenueCat();
  runApp(MyApp());
}

Future<void> _initializeRevenueCat() async {
  await Purchases.setLogLevel(LogLevel.debug);

  PurchasesConfiguration? configuration;
  if (Platform.isAndroid) {
    configuration = PurchasesConfiguration(_androidApiKey);
  } else if (Platform.isIOS) {
    configuration = PurchasesConfiguration(_iosApiKey);
  } else {
    print('RevenueCat not configured for this platform.');
    return;
  }

  try {
    await Purchases.configure(configuration);
    print('RevenueCat configured successfully!');
  } catch (e) {
    print('Error configuring RevenueCat: ${"$"}e');
  }
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter App',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatelessWidget {
  const MyHomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Home'),
      ),
      body: const Center(
        child: Text('Hello, World!'),
      ),
    );
  }
}
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
}
