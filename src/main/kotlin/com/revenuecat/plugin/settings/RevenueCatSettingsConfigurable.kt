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

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.revenuecat.plugin.api.RevenueCatApiClient
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.swing.JComponent

/**
 * Settings configurable for RevenueCat plugin
 */
class RevenueCatSettingsConfigurable : Configurable {

  private var settingsComponent: RevenueCatSettingsComponent? = null

  override fun getDisplayName(): String = "RevenueCat"

  override fun createComponent(): JComponent {
    val component = RevenueCatSettingsComponent()
    settingsComponent = component
    return component.getPanel()
  }

  override fun isModified(): Boolean {
    val settings = RevenueCatSettingsState.getInstance()
    val component = settingsComponent ?: return false

    return component.getApiKey() != settings.apiKey ||
      component.getSdkApiKey() != settings.sdkApiKey ||
      component.getProjectId() != settings.projectId ||
      component.isNotificationsEnabled() != settings.enableNotifications ||
      component.isWebhookEnabled() != settings.enableWebhook ||
      component.getWebhookPort() != settings.webhookPort ||
      component.getNgrokPath() != settings.ngrokPath ||
      component.isAIEnabled() != settings.aiEnabled ||
      component.getAIProvider() != settings.aiProvider ||
      component.getAIModel() != settings.aiModel ||
      component.getAIApiKey() != settings.aiApiKey
  }

  override fun apply() {
    val settings = RevenueCatSettingsState.getInstance()
    val component = settingsComponent ?: return

    val apiKey = component.getApiKey()
    val projectId = component.getProjectId()

    // Validate API key and project ID if provided
    if (apiKey.isNotBlank() && projectId.isNotBlank()) {
      val client = RevenueCatApiClient(apiKey)
      val result = client.testConnection(projectId, null)

      if (result.isFailure) {
        val error = result.exceptionOrNull()
        Messages.showErrorDialog(
          "Failed to connect to RevenueCat API:\n\n${error?.message}",
          "Connection Test Failed",
        )
        return
      } else {
        Messages.showInfoMessage(
          "Successfully connected to RevenueCat API!\n\n" +
            "Your API key and project ID are valid.\n" +
            "The plugin will use the Metrics API to show MRR, ARR, and other overview data.",
          "Connection Test Successful",
        )
      }
    }

    // Save settings
    settings.apiKey = apiKey
    settings.sdkApiKey = component.getSdkApiKey()
    settings.projectId = projectId
    settings.enableNotifications = component.isNotificationsEnabled()
    settings.enableWebhook = component.isWebhookEnabled()
    settings.webhookPort = component.getWebhookPort()
    settings.ngrokPath = component.getNgrokPath()

    // Save AI settings
    settings.aiEnabled = component.isAIEnabled()
    settings.aiProvider = component.getAIProvider()
    settings.aiModel = component.getAIModel()
    settings.aiApiKey = component.getAIApiKey()

    // Force reinitialize the API client with new settings
    com.revenuecat.plugin.services.RevenueCatApiService.getInstance().reinitializeClient()
  }

  override fun reset() {
    val settings = RevenueCatSettingsState.getInstance()
    val component = settingsComponent ?: return

    component.setApiKey(settings.apiKey)
    component.setSdkApiKey(settings.sdkApiKey)
    component.setProjectId(settings.projectId)
    component.setNotificationsEnabled(settings.enableNotifications)
    component.setWebhookEnabled(settings.enableWebhook)
    component.setWebhookPort(settings.webhookPort)
    component.setNgrokPath(settings.ngrokPath)

    // Load AI settings
    component.setAIEnabled(settings.aiEnabled)
    component.setAIProvider(settings.aiProvider)
    component.setAIModel(settings.aiModel)
    component.setAIApiKey(settings.aiApiKey)

    setupWebhookButtons(component)
  }

  /**
   * Setup OAuth authorize button handler
   */
  private fun setupAuthorizeButton(component: RevenueCatSettingsComponent) {
    val authorizeButton = component.getAuthorizeButton()

    // Remove existing listeners to avoid duplicates
    authorizeButton.actionListeners.forEach { authorizeButton.removeActionListener(it) }

    // Add click handler
    authorizeButton.addActionListener {
      startOAuthFlow(component)
    }
  }

  /**
   * Start OAuth authorization flow
   */
  private fun startOAuthFlow(component: RevenueCatSettingsComponent) {
    val settings = RevenueCatSettingsState.getInstance()
    val oauthService = com.revenuecat.plugin.services.RevenueCatOAuthService.getInstance()

    // Run OAuth flow in background task
    com.intellij.openapi.progress.ProgressManager.getInstance().run(
      object : com.intellij.openapi.progress.Task.Backgroundable(
        null,
        "Authorizing with RevenueCat...",
        true,
      ) {
        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
          try {
            indicator.text = "Starting OAuth authorization..."

            // Generate authorization URL with PKCE
            val scopes = listOf(
              "project_configuration:offerings:read",
              "project_configuration:apps:read",
            )
            val (authUrl, codeVerifier) = oauthService.generateAuthorizationUrl(scopes)

            // Extract state from URL for callback server
            val stateParam = authUrl.substringAfter("state=").substringBefore("&")

            // Start local callback server
            indicator.text = "Starting local callback server on port 48888..."
            val callbackServer = com.revenuecat.plugin.services.OAuthCallbackServer(
              port = 48888,
              expectedState = stateParam,
            )
            callbackServer.start()

            // Open browser for authorization
            indicator.text = "Opening browser for authorization..."
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
              com.intellij.ide.BrowserUtil.browse(authUrl)
            }

            // Wait for callback (with 5 minute timeout)
            indicator.text = "Waiting for authorization in browser..."
            val callbackResult = callbackServer.waitForCallback(300)

            when (callbackResult) {
              is com.revenuecat.plugin.services.OAuthCallbackResult.Success -> {
                indicator.text = "Exchanging authorization code for tokens..."

                // Exchange code for tokens
                val tokenResult = oauthService.exchangeCodeForToken(
                  callbackResult.code,
                  codeVerifier,
                )

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                  if (tokenResult.isSuccess) {
                    val tokenResponse = tokenResult.getOrNull()!!

                    // Save tokens
                    oauthService.saveTokens(tokenResponse)

                    // Update UI
                    component.setOAuthClientId(settings.oauthClientId)
                    component.setOAuthClientSecret(settings.oauthClientSecret)

                    Messages.showInfoMessage(
                      "Successfully authorized with RevenueCat!\n\n" +
                        "Access token: ${tokenResponse.accessToken.take(15)}...\n" +
                        "Token type: ${tokenResponse.tokenType}\n" +
                        "Expires in: ${tokenResponse.expiresIn} seconds\n" +
                        "Scopes: ${tokenResponse.scope ?: "default"}\n\n" +
                        "The plugin will now use OAuth tokens to access paywall builder links.",
                      "OAuth Authorization Successful",
                    )
                  } else {
                    val error = tokenResult.exceptionOrNull()
                    Messages.showErrorDialog(
                      "Failed to exchange authorization code for tokens:\n\n${error?.message}",
                      "OAuth Token Exchange Failed",
                    )
                  }
                }
              }
              is com.revenuecat.plugin.services.OAuthCallbackResult.Error -> {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                  Messages.showErrorDialog(
                    "OAuth authorization failed:\n\n${callbackResult.message}",
                    "OAuth Authorization Failed",
                  )
                }
              }
            }
          } catch (e: Exception) {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
              Messages.showErrorDialog(
                "Failed to start OAuth flow:\n\n${e.message}\n\n" +
                  "Make sure port 48888 is not in use by another application.",
                "OAuth Flow Error",
              )
            }
          }
        }
      },
    )
  }

  /**
   * Setup webhook button handlers
   */
  private fun setupWebhookButtons(component: RevenueCatSettingsComponent) {
    val webhookServer = com.revenuecat.plugin.services.RevenueCatWebhookServer.getInstance()
    val startButton = component.getStartWebhookButton()
    val stopButton = component.getStopWebhookButton()
    val startNgrokButton = component.getStartNgrokButton()

    // Remove existing listeners
    startButton.actionListeners.forEach { startButton.removeActionListener(it) }
    stopButton.actionListeners.forEach { stopButton.removeActionListener(it) }
    startNgrokButton.actionListeners.forEach { startNgrokButton.removeActionListener(it) }

    // Start webhook server
    startButton.addActionListener {
      val port = component.getWebhookPort()
      val success = webhookServer.start(port)

      if (success) {
        // Save auto-start preference
        val settings = RevenueCatSettingsState.getInstance()
        settings.autoStartWebhook = true

        component.updateWebhookStatus()
        Messages.showInfoMessage(
          "Webhook server started successfully on port $port.\n\n" +
            "The webhook server will automatically start when you reopen the IDE.\n\n" +
            "To configure RevenueCat webhook:\n" +
            "1. Go to https://app.revenuecat.com/projects/YOUR_PROJECT/integrations/webhooks\n" +
            "2. For local testing, use ngrok to create a public URL\n" +
            "3. Set webhook URL to: http://YOUR_NGROK_URL/revenuecat/webhook",
          "Webhook Server Started",
        )
      }
    }

    // Stop webhook server
    stopButton.addActionListener {
      webhookServer.stop()

      // Clear auto-start preference
      val settings = RevenueCatSettingsState.getInstance()
      settings.autoStartWebhook = false

      component.updateWebhookStatus()
    }

    // Start ngrok
    startNgrokButton.addActionListener {
      val ngrokPath = component.getNgrokPath()
      val port = component.getWebhookPort()

      if (ngrokPath.isBlank()) {
        Messages.showErrorDialog(
          "Please specify the path to ngrok executable.\n\n" +
            "Download ngrok from: https://ngrok.com/download",
          "ngrok Path Required",
        )
        return@addActionListener
      }

      // Start ngrok in background
      com.intellij.openapi.progress.ProgressManager.getInstance().run(
        object : com.intellij.openapi.progress.Task.Backgroundable(
          null,
          "Starting ngrok tunnel...",
          true,
        ) {
          override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
            var process: Process? = null
            try {
              indicator.text = "Starting ngrok on port $port..."

              val processBuilder = ProcessBuilder(ngrokPath, "http", port.toString())
              processBuilder.redirectErrorStream(true)
              process = processBuilder.start()

              // Collect process output to check for errors
              val outputReader = java.io.BufferedReader(
                java.io.InputStreamReader(process.inputStream),
              )
              val errorCollector = StringBuilder()

              // Wait for ngrok to start and retry multiple times
              var publicUrl: String? = null
              var attempts = 0
              val maxAttempts = 10

              while (publicUrl == null && attempts < maxAttempts) {
                attempts++
                indicator.text = "Waiting for ngrok to start (attempt $attempts/$maxAttempts)..."

                // Check if process has exited with error
                if (process.isAlive == false) {
                  // Read any error output
                  while (outputReader.ready()) {
                    errorCollector.append(outputReader.readLine()).append("\n")
                  }
                  throw Exception("ngrok exited unexpectedly:\n$errorCollector")
                }

                Thread.sleep(1000)

                try {
                  // Try to get the public URL from ngrok API
                  val ngrokApiUrl = java.net.URL("http://localhost:4040/api/tunnels")
                  val connection = ngrokApiUrl.openConnection() as java.net.HttpURLConnection
                  connection.requestMethod = "GET"
                  connection.connectTimeout = 1000
                  connection.readTimeout = 1000

                  val response = connection.inputStream.bufferedReader().use { it.readText() }
                  publicUrl = extractNgrokUrl(response)
                } catch (e: Exception) {
                  // Ngrok not ready yet, will retry
                  if (attempts >= maxAttempts) {
                    // Try to read error output before giving up
                    while (outputReader.ready()) {
                      errorCollector.append(outputReader.readLine()).append("\n")
                    }
                    if (errorCollector.isNotEmpty()) {
                      throw Exception("Failed to connect to ngrok:\n$errorCollector")
                    }
                    throw e
                  }
                }
              }

              com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                if (publicUrl != null) {
                  // Save auto-start preference
                  val settings = RevenueCatSettingsState.getInstance()
                  settings.autoStartNgrok = true

                  val webhooksUrl =
                    "https://app.revenuecat.com/projects/YOUR_PROJECT/" +
                      "integrations/webhooks"
                  val webhookEndpoint = "$publicUrl/revenuecat/webhook"
                  Messages.showInfoMessage(
                    "ngrok tunnel started successfully!\n\n" +
                      "Public URL: $publicUrl\n\n" +
                      "ngrok will automatically start when you reopen the IDE.\n\n" +
                      "Configure RevenueCat webhook:\n" +
                      "1. Go to: $webhooksUrl\n" +
                      "2. Set webhook URL to: $webhookEndpoint\n\n" +
                      "The URL has been copied to your clipboard.",
                    "ngrok Tunnel Started",
                  )

                  // Copy URL to clipboard
                  val clipboard =
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                  val selection =
                    java.awt.datatransfer.StringSelection(webhookEndpoint)
                  clipboard.setContents(selection, null)
                } else {
                  Messages.showWarningDialog(
                    "ngrok started but couldn't retrieve the public URL.\n\n" +
                      "Check ngrok web interface at: http://localhost:4040",
                    "ngrok Started",
                  )
                }
              }
            } catch (e: Exception) {
              com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(
                  "Failed to start ngrok:\n\n${e.message}\n\n" +
                    "Make sure ngrok is installed and the path is correct.",
                  "ngrok Error",
                )
              }
            }
          }
        },
      )
    }
  }

  /**
   * Extract ngrok public URL from API response
   */
  private fun extractNgrokUrl(jsonResponse: String): String? {
    return try {
      val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
      val response = json.parseToJsonElement(jsonResponse).jsonObject
      val tunnels = response["tunnels"]?.jsonArray

      tunnels?.firstOrNull { tunnel ->
        tunnel.jsonObject["proto"]?.jsonPrimitive?.content == "https"
      }?.jsonObject?.get("public_url")?.jsonPrimitive?.content
    } catch (e: Exception) {
      null
    }
  }

  override fun disposeUIResources() {
    settingsComponent = null
  }
}
