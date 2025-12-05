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
import com.intellij.ui.components.JBPanel
import java.awt.CardLayout
import javax.swing.Action
import javax.swing.JComponent

/**
 * Multi-step wizard for integrating RevenueCat SDK
 */
class SdkIntegrationWizard(private val project: Project) : DialogWrapper(project) {

  private val cardLayout = CardLayout()
  private val contentPanel = JBPanel<JBPanel<*>>(cardLayout)

  private var currentStep = WizardStep.PLATFORM_SELECTION

  private lateinit var platformSelectionPanel: PlatformSelectionPanel
  private lateinit var androidConfigPanel: AndroidConfigurationPanel
  private lateinit var kotlinConfigPanel: KotlinConfigurationPanel
  private lateinit var flutterConfigPanel: FlutterConfigurationPanel
  private lateinit var paywallOptionPanel: PaywallOptionPanel
  private lateinit var androidPaywallConfigPanel: AndroidPaywallConfigPanel
  private lateinit var kmpPaywallConfigPanel: KmpPaywallConfigPanel
  private lateinit var flutterPaywallConfigPanel: FlutterPaywallConfigPanel
  private lateinit var completionPanel: CompletionPanel

  private var selectedPlatform: Platform? = null
  private var setupPaywall: Boolean = false

  // Always use DEFAULT (full screen) paywall template
  private val selectedPaywallTemplate: PaywallTemplate = PaywallTemplate.DEFAULT

  private lateinit var backAction: DialogWrapperAction

  init {
    title = "RevenueCat SDK Integration Wizard"
    setSize(800, 550)
    init()
  }

  override fun createCenterPanel(): JComponent {
    setupPanels()
    return contentPanel
  }

  private fun setupPanels() {
    // Step 1: Platform Selection
    platformSelectionPanel = PlatformSelectionPanel { platform ->
      selectedPlatform = platform
      nextStep()
    }
    contentPanel.add(platformSelectionPanel, WizardStep.PLATFORM_SELECTION.name)

    // Step 2a: Android Configuration
    androidConfigPanel = AndroidConfigurationPanel(project) {
      nextStep()
    }
    contentPanel.add(androidConfigPanel, WizardStep.ANDROID_CONFIG.name)

    // Step 2b: Kotlin Multiplatform Configuration
    kotlinConfigPanel = KotlinConfigurationPanel(project) {
      nextStep()
    }
    contentPanel.add(kotlinConfigPanel, WizardStep.KOTLIN_CONFIG.name)

    // Step 2c: Flutter Configuration
    flutterConfigPanel = FlutterConfigurationPanel(project) {
      nextStep()
    }
    contentPanel.add(flutterConfigPanel, WizardStep.FLUTTER_CONFIG.name)

    // Step 3: Paywall Option (ask if user wants to set up paywall)
    paywallOptionPanel = PaywallOptionPanel { wantsPaywall ->
      setupPaywall = wantsPaywall
      nextStep()
    }
    contentPanel.add(paywallOptionPanel, WizardStep.PAYWALL_OPTION.name)

    // Step 4: Android Paywall Configuration (always uses full screen paywall)
    androidPaywallConfigPanel = AndroidPaywallConfigPanel(project, { selectedPaywallTemplate }) {
      nextStep()
    }
    contentPanel.add(androidPaywallConfigPanel, WizardStep.ANDROID_PAYWALL_CONFIG.name)

    // Step 5b: KMP Paywall Configuration
    kmpPaywallConfigPanel = KmpPaywallConfigPanel(project, { selectedPaywallTemplate }) {
      nextStep()
    }
    contentPanel.add(kmpPaywallConfigPanel, WizardStep.KMP_PAYWALL_CONFIG.name)

    // Step 5c: Flutter Paywall Configuration
    flutterPaywallConfigPanel = FlutterPaywallConfigPanel(project, { selectedPaywallTemplate }) {
      nextStep()
    }
    contentPanel.add(flutterPaywallConfigPanel, WizardStep.FLUTTER_PAYWALL_CONFIG.name)

    // Step 6: Completion
    completionPanel = CompletionPanel()
    contentPanel.add(completionPanel, WizardStep.COMPLETION.name)
  }

  private fun nextStep() {
    currentStep = when (currentStep) {
      WizardStep.PLATFORM_SELECTION -> {
        when (selectedPlatform) {
          Platform.ANDROID -> WizardStep.ANDROID_CONFIG
          Platform.KOTLIN -> WizardStep.KOTLIN_CONFIG
          Platform.FLUTTER -> WizardStep.FLUTTER_CONFIG
          // Paywall-only platforms skip SDK config and go directly to paywall config
          Platform.ANDROID_PAYWALL -> {
            setupPaywall = true
            WizardStep.ANDROID_PAYWALL_CONFIG
          }
          Platform.KOTLIN_PAYWALL -> {
            setupPaywall = true
            WizardStep.KMP_PAYWALL_CONFIG
          }
          Platform.FLUTTER_PAYWALL -> {
            setupPaywall = true
            WizardStep.FLUTTER_PAYWALL_CONFIG
          }
          null -> WizardStep.PLATFORM_SELECTION
        }
      }
      WizardStep.ANDROID_CONFIG, WizardStep.KOTLIN_CONFIG, WizardStep.FLUTTER_CONFIG -> {
        WizardStep.PAYWALL_OPTION
      }
      WizardStep.PAYWALL_OPTION -> {
        if (setupPaywall) {
          // Go directly to paywall config for the selected platform
          when (selectedPlatform) {
            Platform.ANDROID, Platform.ANDROID_PAYWALL -> WizardStep.ANDROID_PAYWALL_CONFIG
            Platform.KOTLIN, Platform.KOTLIN_PAYWALL -> WizardStep.KMP_PAYWALL_CONFIG
            Platform.FLUTTER, Platform.FLUTTER_PAYWALL -> WizardStep.FLUTTER_PAYWALL_CONFIG
            null -> WizardStep.COMPLETION
          }
        } else {
          completionPanel.setPaywallConfigured(false)
          WizardStep.COMPLETION
        }
      }
      WizardStep.ANDROID_PAYWALL_CONFIG, WizardStep.KMP_PAYWALL_CONFIG, WizardStep.FLUTTER_PAYWALL_CONFIG -> {
        completionPanel.setPaywallConfigured(true)
        WizardStep.COMPLETION
      }
      WizardStep.COMPLETION -> {
        close(OK_EXIT_CODE)
        return
      }
    }

    cardLayout.show(contentPanel, currentStep.name)
    updateButtons()
  }

  private fun previousStep() {
    currentStep = when (currentStep) {
      WizardStep.PLATFORM_SELECTION -> return
      WizardStep.ANDROID_CONFIG, WizardStep.KOTLIN_CONFIG, WizardStep.FLUTTER_CONFIG -> WizardStep.PLATFORM_SELECTION
      WizardStep.PAYWALL_OPTION -> {
        when (selectedPlatform) {
          Platform.ANDROID -> WizardStep.ANDROID_CONFIG
          Platform.KOTLIN -> WizardStep.KOTLIN_CONFIG
          Platform.FLUTTER -> WizardStep.FLUTTER_CONFIG
          else -> WizardStep.PLATFORM_SELECTION
        }
      }
      WizardStep.ANDROID_PAYWALL_CONFIG, WizardStep.KMP_PAYWALL_CONFIG, WizardStep.FLUTTER_PAYWALL_CONFIG -> {
        // For paywall-only platforms, go back to platform selection
        if (selectedPlatform?.isPaywallOnly == true) {
          WizardStep.PLATFORM_SELECTION
        } else {
          WizardStep.PAYWALL_OPTION
        }
      }
      WizardStep.COMPLETION -> {
        if (setupPaywall) {
          when (selectedPlatform) {
            Platform.ANDROID, Platform.ANDROID_PAYWALL -> WizardStep.ANDROID_PAYWALL_CONFIG
            Platform.KOTLIN, Platform.KOTLIN_PAYWALL -> WizardStep.KMP_PAYWALL_CONFIG
            Platform.FLUTTER, Platform.FLUTTER_PAYWALL -> WizardStep.FLUTTER_PAYWALL_CONFIG
            null -> WizardStep.PAYWALL_OPTION
          }
        } else {
          WizardStep.PAYWALL_OPTION
        }
      }
    }

    cardLayout.show(contentPanel, currentStep.name)
    updateButtons()
  }

  private fun updateButtons() {
    // Finish button only enabled on completion step
    isOKActionEnabled = currentStep == WizardStep.COMPLETION
    setOKButtonText("Finish")

    // Back button disabled on first step
    if (::backAction.isInitialized) {
      backAction.isEnabled = currentStep != WizardStep.PLATFORM_SELECTION
    }
  }

  override fun createActions(): Array<Action> {
    backAction = object : DialogWrapperAction("Back") {
      override fun doAction(e: java.awt.event.ActionEvent?) {
        previousStep()
      }
    }
    // Disable back button initially since we start on first step
    backAction.isEnabled = false

    return arrayOf(
      okAction.apply {
        putValue(Action.NAME, "Finish")
      },
      backAction,
      object : DialogWrapperAction("Next") {
        override fun doAction(e: java.awt.event.ActionEvent?) {
          // Next button is always disabled - navigation happens via panel selections
        }
      }.apply {
        isEnabled = false
      },
      cancelAction,
    )
  }

  override fun doOKAction() {
    if (currentStep == WizardStep.COMPLETION) {
      close(OK_EXIT_CODE)
    }
  }

  enum class WizardStep {
    PLATFORM_SELECTION,
    ANDROID_CONFIG,
    KOTLIN_CONFIG,
    FLUTTER_CONFIG,
    PAYWALL_OPTION,
    ANDROID_PAYWALL_CONFIG,
    KMP_PAYWALL_CONFIG,
    FLUTTER_PAYWALL_CONFIG,
    COMPLETION,
  }

  enum class Platform(val displayName: String, val isPaywallOnly: Boolean = false) {
    ANDROID("Android (Kotlin)"),
    KOTLIN("Kotlin Multiplatform"),
    FLUTTER("Flutter"),
    ANDROID_PAYWALL("Android Paywall", true),
    KOTLIN_PAYWALL("KMP Paywall", true),
    FLUTTER_PAYWALL("Flutter Paywall", true),
  }

  enum class PaywallTemplate(
    val displayName: String,
    val description: String,
  ) {
    DEFAULT("Full Screen Paywall", "RevenueCat's default full-screen paywall presentation"),
  }
}
