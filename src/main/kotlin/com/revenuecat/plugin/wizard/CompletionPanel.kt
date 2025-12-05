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

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Completion panel shown after successful SDK integration
 */
class CompletionPanel : JBPanel<JBPanel<*>>() {

  private var paywallConfigured: Boolean = false

  init {
    layout = BorderLayout()
    border = JBUI.Borders.empty(20)

    setupUI()
  }

  fun setPaywallConfigured(configured: Boolean) {
    paywallConfigured = configured
    removeAll()
    setupUI()
    revalidate()
    repaint()
  }

  private fun setupUI() {
    val mainPanel = JBPanel<JBPanel<*>>()
    mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

    // Success icon and title
    val titleLabel = JBLabel("<html><h1>✅ Setup Complete!</h1></html>")
    titleLabel.alignmentX = 0.5f
    mainPanel.add(titleLabel)
    mainPanel.add(Box.createVerticalStrut(20))

    // Shippy image - center aligned
    try {
      val shippyStream = javaClass.getResourceAsStream("/images/shippy_4.png")
      if (shippyStream != null) {
        val shippyImage = ImageIO.read(shippyStream)
        val scaledImage = shippyImage.getScaledInstance(150, 150, java.awt.Image.SCALE_SMOOTH)

        // Create a container panel to ensure proper centering
        val shippyContainer = JBPanel<JBPanel<*>>()
        shippyContainer.layout = BoxLayout(shippyContainer, BoxLayout.X_AXIS)
        shippyContainer.alignmentX = 0.5f

        val shippyLabel = JBLabel(ImageIcon(scaledImage))
        shippyContainer.add(Box.createHorizontalGlue())
        shippyContainer.add(shippyLabel)
        shippyContainer.add(Box.createHorizontalGlue())

        mainPanel.add(shippyContainer)
        mainPanel.add(Box.createVerticalStrut(20))
      }
    } catch (e: Exception) {
      // If image loading fails, continue without shippy
      e.printStackTrace()
    }

    // Success message
    val messageText = if (paywallConfigured) {
      "<html>" +
        "Your project is now configured with RevenueCat SDK and Paywall UI! " +
        "You're ready to start implementing in-app purchases." +
        "</html>"
    } else {
      "<html>" +
        "Your project is now configured with RevenueCat SDK! " +
        "You're ready to start implementing in-app purchases." +
        "</html>"
    }
    val messageLabel = JBLabel(messageText)
    messageLabel.alignmentX = 0.5f
    mainPanel.add(messageLabel)
    mainPanel.add(Box.createVerticalStrut(30))

    // Next steps
    val nextStepsPanel = createNextStepsPanel()
    nextStepsPanel.alignmentX = 0.5f
    mainPanel.add(nextStepsPanel)
    mainPanel.add(Box.createVerticalStrut(30))

    // Resource links
    val linksPanel = createResourceLinksPanel()
    linksPanel.alignmentX = 0.5f
    mainPanel.add(linksPanel)

    mainPanel.add(Box.createVerticalGlue())

    add(mainPanel, BorderLayout.CENTER)
  }

  private fun createNextStepsPanel(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = BorderFactory.createTitledBorder("Next Steps")
    panel.alignmentX = 0.0f
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 200)

    val steps = if (paywallConfigured) {
      listOf(
        "1. Sync your project dependencies",
        "2. Configure your products in RevenueCat dashboard",
        "3. Add the paywall to your app navigation",
        "4. Configure your paywall appearance in the dashboard",
        "5. Test purchases with sandbox/test users",
      )
    } else {
      listOf(
        "1. Sync your project dependencies",
        "2. Configure your products in RevenueCat dashboard",
        "3. Create offerings and paywalls",
        "4. Implement purchase flows in your app",
        "5. Test with sandbox/test users",
      )
    }

    steps.forEach { step ->
      val stepLabel = JBLabel(step)
      stepLabel.alignmentX = 0.0f
      stepLabel.border = JBUI.Borders.empty(5)
      panel.add(stepLabel)
    }

    return panel
  }

  private fun createResourceLinksPanel(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = BorderFactory.createTitledBorder("💡 Helpful Resources")
    panel.alignmentX = 0.0f
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 200)

    val links = mapOf(
      "📚 Getting Started Guide" to "https://www.revenuecat.com/docs/getting-started",
      "📖 SDK Documentation" to "https://www.revenuecat.com/docs/android",
      "🎓 RevenueCat Codelabs" to "https://revenuecat.github.io/",
      "💬 Community Support" to "https://community.revenuecat.com/",
      "🐛 Report Issues" to "https://github.com/RevenueCat/purchases-android/issues",
    )

    links.forEach { (title, url) ->
      val linkButton = JButton("<html><u><nobr>$title</nobr></u></html>")
      linkButton.isBorderPainted = false
      linkButton.isContentAreaFilled = false
      linkButton.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
      linkButton.horizontalAlignment = SwingConstants.LEFT
      linkButton.alignmentX = 0.0f
      linkButton.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 30)
      linkButton.preferredSize = java.awt.Dimension(400, 30)
      linkButton.addActionListener {
        BrowserUtil.browse(url)
      }
      panel.add(linkButton)
    }

    return panel
  }
}
