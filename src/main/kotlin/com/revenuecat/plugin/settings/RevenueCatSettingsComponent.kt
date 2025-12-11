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
package com.revenuecat.plugin.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.revenuecat.plugin.ai.AIModelOption
import com.revenuecat.plugin.ai.AIProvider
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * UI component for RevenueCat settings
 */
class RevenueCatSettingsComponent {

  private val mainPanel: JPanel
  private val apiKeyField = JBPasswordField()
  private val sdkApiKeyField = JBPasswordField()
  private val projectIdField = JBTextField()
  private val enableNotificationsCheckBox = JBCheckBox("Enable purchase notifications")

  // Webhook fields
  private val enableWebhookCheckBox = JBCheckBox("Enable real-time webhook notifications")
  private val webhookPortSpinner = JSpinner(SpinnerNumberModel(48889, 1024, 65535, 1))
  private val webhookStatusLabel = JBLabel()
  private val startWebhookButton = JButton("Start Webhook Server")
  private val stopWebhookButton = JButton("Stop Webhook Server")
  private val ngrokPathField = JBTextField()
  private val browseNgrokButton = JButton("Browse...")
  private val startNgrokButton = JButton("Start ngrok")

  // OAuth 2.0 fields
  private val oauthClientIdField = JBTextField()
  private val oauthClientSecretField = JBPasswordField()
  private val oauthStatusLabel = JBLabel()
  private val authorizeButton = JButton("Authorize with RevenueCat")

  // AI Assistant fields
  private val enableAICheckBox = JBCheckBox("Enable AI Assistant")
  private val aiProviderCombo = JComboBox(AIProvider.entries.map { it.displayName }.toTypedArray())
  private val aiModelCombo = JComboBox<String>()
  private val aiApiKeyField = JBPasswordField()

  init {
    mainPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(
        JBLabel("API Key (Secret Key):"),
        apiKeyField,
        1,
        false,
      )
      .addComponentToRightColumn(
        JBLabel("<html><small>Your RevenueCat v2 API secret key (starts with sk_)</small></html>"),
        0,
      )
      .addSeparator(10)
      .addLabeledComponent(
        JBLabel("SDK API Key:"),
        sdkApiKeyField,
        1,
        false,
      )
      .addComponentToRightColumn(
        JBLabel("<html><small>Platform-specific key (goog_, appl_, amzn_, stripe_)</small></html>"),
        0,
      )
      .addSeparator(10)
      .addLabeledComponent(
        JBLabel("Project ID:"),
        projectIdField,
        1,
        false,
      )
      .addComponentToRightColumn(
        JBLabel("<html><small>Your RevenueCat project ID</small></html>"),
        0,
      )
      .addSeparator(10)
      .addComponent(enableNotificationsCheckBox, 0)
      .addSeparator(20)
      .addComponent(JBLabel("<html><b>Webhook Settings (Recommended)</b></html>"), 0)
      .addComponentToRightColumn(
        JBLabel(
          "<html><small>Receive real-time notifications instead of polling. " +
            "More efficient and instant!</small></html>",
        ),
        0,
      )
      .addSeparator(10)
      .addComponent(enableWebhookCheckBox, 0)
      .addSeparator(5)
      .addLabeledComponent(
        JBLabel("Webhook Port:"),
        webhookPortSpinner,
        1,
        false,
      )
      .addComponentToRightColumn(
        JBLabel("<html><small>Local port for webhook server (default: 48889)</small></html>"),
        0,
      )
      .addSeparator(5)
      .addComponent(webhookStatusLabel, 0)
      .addSeparator(5)
      .addComponent(createWebhookButtonPanel(), 0)
      .addSeparator(10)
      .addLabeledComponent(
        JBLabel("ngrok Path (Optional):"),
        createNgrokPathPanel(),
        1,
        false,
      )
      .addComponentToRightColumn(
        JBLabel("<html><small>Path to ngrok executable for public webhook URL</small></html>"),
        0,
      )
      .addSeparator(5)
      .addComponent(startNgrokButton, 0)
      .addSeparator(20)
      .addComponent(JBLabel("<html><b>AI Assistant Settings</b></html>"), 0)
      .addComponentToRightColumn(
        JBLabel(
          "<html><small>Use AI to query your RevenueCat metrics and get help</small></html>",
        ),
        0,
      )
      .addSeparator(10)
      .addComponent(enableAICheckBox, 0)
      .addSeparator(5)
      .addLabeledComponent(
        JBLabel("AI Provider:"),
        aiProviderCombo,
        1,
        false,
      )
      .addSeparator(5)
      .addLabeledComponent(
        JBLabel("Model:"),
        aiModelCombo,
        1,
        false,
      )
      .addSeparator(5)
      .addLabeledComponent(
        JBLabel("API Key:"),
        aiApiKeyField,
        1,
        false,
      )
      .addComponentToRightColumn(
        JBLabel("<html><small>Your AI platform API key</small></html>"),
        0,
      )
      .addComponentFillVertically(JPanel(), 0)
      .panel

    enableNotificationsCheckBox.isSelected = true
    setupAIControls()
    stopWebhookButton.isEnabled = false

    // Add listener to enable/disable webhook controls based on notifications checkbox
    enableNotificationsCheckBox.addActionListener {
      updateWebhookControlsState()
    }

    updateWebhookStatus()
    updateWebhookControlsState()
  }

  private fun updateWebhookControlsState() {
    val enabled = enableNotificationsCheckBox.isSelected
    enableWebhookCheckBox.isEnabled = enabled
    webhookPortSpinner.isEnabled = enabled && enableWebhookCheckBox.isSelected
    startWebhookButton.isEnabled = enabled && enableWebhookCheckBox.isSelected
    stopWebhookButton.isEnabled = enabled && enableWebhookCheckBox.isSelected
    ngrokPathField.isEnabled = enabled && enableWebhookCheckBox.isSelected
    browseNgrokButton.isEnabled = enabled && enableWebhookCheckBox.isSelected
    startNgrokButton.isEnabled = enabled && enableWebhookCheckBox.isSelected

    // Update webhook status
    if (enabled) {
      updateWebhookStatus()
    }

    // Also add listener to webhook checkbox
    enableWebhookCheckBox.addActionListener {
      updateWebhookControlsState()
    }
  }

  private fun createWebhookButtonPanel(): JPanel {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.add(startWebhookButton)
    panel.add(Box.createHorizontalStrut(5))
    panel.add(stopWebhookButton)
    return panel
  }

  private fun createNgrokPathPanel(): JPanel {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.add(ngrokPathField)
    panel.add(Box.createHorizontalStrut(5))
    panel.add(browseNgrokButton)

    // Add file chooser action
    browseNgrokButton.addActionListener {
      val descriptor = com.intellij.openapi.fileChooser
        .FileChooserDescriptorFactory.createSingleFileDescriptor()
      val fileChooser = com.intellij.openapi.fileChooser
        .FileChooserFactory.getInstance()
        .createFileChooser(descriptor, null, panel)

      val files = fileChooser.choose(null)
      if (files.isNotEmpty()) {
        ngrokPathField.text = files[0].path
      }
    }

    return panel
  }

  fun getPanel(): JPanel = mainPanel

  fun getApiKey(): String = String(apiKeyField.password)

  fun setApiKey(apiKey: String) {
    apiKeyField.text = apiKey
  }

  fun getSdkApiKey(): String = String(sdkApiKeyField.password)

  fun setSdkApiKey(sdkApiKey: String) {
    sdkApiKeyField.text = sdkApiKey
  }

  fun getProjectId(): String = projectIdField.text

  fun setProjectId(projectId: String) {
    projectIdField.text = projectId
  }

  fun isNotificationsEnabled(): Boolean = enableNotificationsCheckBox.isSelected

  fun setNotificationsEnabled(enabled: Boolean) {
    enableNotificationsCheckBox.isSelected = enabled
  }

  // Webhook getters/setters
  fun isWebhookEnabled(): Boolean = enableWebhookCheckBox.isSelected

  fun setWebhookEnabled(enabled: Boolean) {
    enableWebhookCheckBox.isSelected = enabled
    updateWebhookStatus()
  }

  fun getWebhookPort(): Int = webhookPortSpinner.value as Int

  fun setWebhookPort(port: Int) {
    webhookPortSpinner.value = port
  }

  fun getNgrokPath(): String = ngrokPathField.text

  fun setNgrokPath(path: String) {
    ngrokPathField.text = path
  }

  fun getStartWebhookButton(): JButton = startWebhookButton

  fun getStopWebhookButton(): JButton = stopWebhookButton

  fun getStartNgrokButton(): JButton = startNgrokButton

  fun updateWebhookStatus() {
    val server = com.revenuecat.plugin.services.RevenueCatWebhookServer.getInstance()

    if (server.isRunning()) {
      val text = "<html><font color='green'>Webhook server is running</font></html>"
      webhookStatusLabel.text = text
      startWebhookButton.isEnabled = false
      stopWebhookButton.isEnabled = true
    } else {
      val text = "<html><font color='gray'>Webhook server is not running</font></html>"
      webhookStatusLabel.text = text
      startWebhookButton.isEnabled = true
      stopWebhookButton.isEnabled = false
    }
  }

  // OAuth getters/setters
  fun getOAuthClientId(): String = oauthClientIdField.text

  fun setOAuthClientId(clientId: String) {
    oauthClientIdField.text = clientId
    updateOAuthStatus()
  }

  fun getOAuthClientSecret(): String = String(oauthClientSecretField.password)

  fun setOAuthClientSecret(secret: String) {
    oauthClientSecretField.text = secret
    updateOAuthStatus()
  }

  fun getAuthorizeButton(): JButton = authorizeButton

  /**
   * Update OAuth status label
   */
  private fun updateOAuthStatus() {
    val settings = RevenueCatSettingsState.getInstance()

    when {
      settings.hasValidOAuthToken() -> {
        val msg = "<html><font color='green'>OAuth authorized and token is valid</font></html>"
        oauthStatusLabel.text = msg
        authorizeButton.isEnabled = true
        authorizeButton.text = "Re-authorize"
      }
      settings.canRefreshOAuthToken() -> {
        val msg = "<html><font color='orange'>Token expired but can be refreshed</font></html>"
        oauthStatusLabel.text = msg
        authorizeButton.isEnabled = true
        authorizeButton.text = "Authorize with RevenueCat"
      }
      settings.isOAuthConfigured() -> {
        oauthStatusLabel.text = "<html><font color='gray'>Not authorized yet</font></html>"
        authorizeButton.isEnabled = true
        authorizeButton.text = "Authorize with RevenueCat"
      }
      else -> {
        val msg = "<html><font color='gray'>Configure Client ID and Secret</font></html>"
        oauthStatusLabel.text = msg
        authorizeButton.isEnabled = false
        authorizeButton.text = "Authorize with RevenueCat"
      }
    }
  }

  // AI Settings methods
  private fun setupAIControls() {
    // Initialize model combo based on selected provider
    updateModelCombo()

    // Add listener to update models when provider changes
    aiProviderCombo.addActionListener {
      updateModelCombo()
    }

    // Add listener to enable/disable AI controls
    enableAICheckBox.addActionListener {
      updateAIControlsState()
    }

    updateAIControlsState()
  }

  private fun updateModelCombo() {
    val selectedProviderIndex = aiProviderCombo.selectedIndex
    val selectedProvider = AIProvider.entries[selectedProviderIndex]

    aiModelCombo.removeAllItems()
    AIModelOption.entries
      .filter { it.provider == selectedProvider }
      .forEach { aiModelCombo.addItem(it.displayName) }

    if (aiModelCombo.itemCount > 0) {
      aiModelCombo.selectedIndex = 0
    }
  }

  private fun updateAIControlsState() {
    val enabled = enableAICheckBox.isSelected
    aiProviderCombo.isEnabled = enabled
    aiModelCombo.isEnabled = enabled
    aiApiKeyField.isEnabled = enabled
  }

  // AI getters/setters
  fun isAIEnabled(): Boolean = enableAICheckBox.isSelected

  fun setAIEnabled(enabled: Boolean) {
    enableAICheckBox.isSelected = enabled
    updateAIControlsState()
  }

  fun getAIProvider(): String {
    val selectedIndex = aiProviderCombo.selectedIndex
    return if (selectedIndex >= 0) AIProvider.entries[selectedIndex].name else AIProvider.OPENAI.name
  }

  fun setAIProvider(provider: String) {
    val providerEnum = AIProvider.entries.find { it.name == provider } ?: AIProvider.OPENAI
    aiProviderCombo.selectedIndex = AIProvider.entries.indexOf(providerEnum)
    updateModelCombo()
  }

  fun getAIModel(): String {
    val selectedProviderIndex = aiProviderCombo.selectedIndex
    val selectedProvider = AIProvider.entries[selectedProviderIndex]
    val modelIndex = aiModelCombo.selectedIndex

    val modelsForProvider = AIModelOption.entries.filter { it.provider == selectedProvider }
    return if (modelIndex >= 0 && modelIndex < modelsForProvider.size) {
      modelsForProvider[modelIndex].name
    } else {
      AIModelOption.GPT_4O_MINI.name
    }
  }

  fun setAIModel(model: String) {
    val modelOption = AIModelOption.entries.find { it.name == model }
    if (modelOption != null) {
      // First set the provider
      setAIProvider(modelOption.provider.name)
      // Then find and select the model in the combo
      val modelsForProvider = AIModelOption.entries.filter { it.provider == modelOption.provider }
      val modelIndex = modelsForProvider.indexOf(modelOption)
      if (modelIndex >= 0) {
        aiModelCombo.selectedIndex = modelIndex
      }
    }
  }

  fun getAIApiKey(): String = String(aiApiKeyField.password)

  fun setAIApiKey(apiKey: String) {
    aiApiKeyField.text = apiKey
  }
}
