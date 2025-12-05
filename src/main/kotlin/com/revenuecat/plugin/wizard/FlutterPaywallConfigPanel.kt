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
 * Configuration panel for Flutter Paywall UI integration
 */
class FlutterPaywallConfigPanel(
  private val project: Project,
  private val getSelectedTemplate: () -> SdkIntegrationWizard.PaywallTemplate?,
  private val onComplete: () -> Unit,
) : JBPanel<JBPanel<*>>() {

  private val addDependencyCheckbox = JBCheckBox("Add purchases_ui_flutter to pubspec.yaml", true)
  private val generateCodeCheckbox = JBCheckBox("Generate paywall code template", true)
  private val versionLabel = JBLabel()
  private lateinit var codePreviewTextArea: JTextArea

  private var sdkVersion = "9.9.9" // Default fallback - same version as purchases_flutter SDK

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
        // purchases_ui_flutter uses the same version as purchases_flutter
        sdkVersion = GitHubVersionService.getLatestFlutterVersion()
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
    val titleLabel = JBLabel("<html><h1>Flutter Paywall Setup</h1></html>")
    titleLabel.alignmentX = 0.0f
    mainPanel.add(titleLabel)
    mainPanel.add(Box.createVerticalStrut(10))

    // Description
    val descLabel =
      JBLabel("<html>Configure RevenueCat Paywall UI for your Flutter project.</html>")
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
      # Add to pubspec.yaml
      dependencies:
        purchases_flutter: ^$sdkVersion
        purchases_ui_flutter: ^$sdkVersion

      // Full Screen Paywall Widget
      import 'package:purchases_ui_flutter/purchases_ui_flutter.dart';

      class PaywallScreen extends StatelessWidget {
        @override
        Widget build(BuildContext context) {
          return PaywallView(
            onDismiss: () => Navigator.pop(context),
            onPurchaseCompleted: (customerInfo) {
              // Handle purchase
            },
          );
        }
      }
    """.trimIndent()
  }

  private fun applyConfiguration() {
    ApplicationManager.getApplication().invokeLater {
      val results = mutableListOf<String>()

      try {
        if (addDependencyCheckbox.isSelected) {
          val dependencyResult = addDependencyToPubspec()
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
            "1. Run 'flutter pub get'\n" +
            "2. Add the paywall widget to your app\n" +
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

  private fun addDependencyToPubspec(): String {
    // Find pubspec.yaml in a read action
    val pubspecFile = ApplicationManager.getApplication().runReadAction<com.intellij.openapi.vfs.VirtualFile?> {
      val pubspecFiles = FilenameIndex.getFilesByName(
        project,
        "pubspec.yaml",
        GlobalSearchScope.projectScope(project),
      )
      pubspecFiles.firstOrNull()?.virtualFile
    }

    if (pubspecFile == null) {
      return "Could not find pubspec.yaml"
    }

    var modified = false
    WriteCommandAction.runWriteCommandAction(project) {
      val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
        .getDocument(pubspecFile)

      if (document != null) {
        val text = document.text

        // Check if dependency already exists
        if (!text.contains("purchases_ui_flutter")) {
          // Find dependencies section
          val dependenciesIndex = text.indexOf("dependencies:")
          if (dependenciesIndex != -1) {
            val lineEndIndex = text.indexOf("\n", dependenciesIndex) + 1
            val dependency = "  purchases_ui_flutter: ^$sdkVersion\n"
            document.insertString(lineEndIndex, dependency)
            modified = true
          }
        }
      }
    }

    return if (modified) {
      "Added purchases_ui_flutter to pubspec.yaml"
    } else {
      "Dependency already exists in pubspec.yaml"
    }
  }

  private fun showGeneratedCode() {
    val code = generateFullCode()

    ApplicationManager.getApplication().invokeLater {
      showCodeDialog("Flutter Full Screen Paywall Code", code)
    }
  }

  private fun generateFullCode(): String {
    return """
      import 'package:flutter/material.dart';
      import 'package:purchases_flutter/purchases_flutter.dart';
      import 'package:purchases_ui_flutter/purchases_ui_flutter.dart';

      /// Full-screen paywall presentation
      class PaywallScreen extends StatelessWidget {
        final VoidCallback? onDismiss;
        final Function(CustomerInfo)? onPurchaseCompleted;
        final Function(PurchasesError)? onPurchaseError;
        final Function(CustomerInfo)? onRestoreCompleted;

        const PaywallScreen({
          Key? key,
          this.onDismiss,
          this.onPurchaseCompleted,
          this.onPurchaseError,
          this.onRestoreCompleted,
        }) : super(key: key);

        @override
        Widget build(BuildContext context) {
          return PaywallView(
            onDismiss: onDismiss ?? () => Navigator.pop(context),
            onPurchaseCompleted: (customerInfo) {
              onPurchaseCompleted?.call(customerInfo);
            },
            onPurchaseError: (error) {
              onPurchaseError?.call(error);
            },
            onRestoreCompleted: (customerInfo) {
              onRestoreCompleted?.call(customerInfo);
            },
          );
        }
      }

      // Usage:
      //
      // void showPaywall(BuildContext context) {
      //   Navigator.push(
      //     context,
      //     MaterialPageRoute(
      //       builder: (context) => PaywallScreen(
      //         onPurchaseCompleted: (customerInfo) {
      //           Navigator.pop(context);
      //           // Handle successful purchase
      //         },
      //       ),
      //     ),
      //   );
      // }
      //
      // Or as a modal:
      //
      // void showPaywallModal(BuildContext context) {
      //   showModalBottomSheet(
      //     context: context,
      //     isScrollControlled: true,
      //     builder: (context) => PaywallScreen(),
      //   );
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
