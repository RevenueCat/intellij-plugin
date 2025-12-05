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

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Panel for selecting the platform (Android, KMP, Flutter) or Paywall-only options
 */
class PlatformSelectionPanel(
  private val onPlatformSelected: (SdkIntegrationWizard.Platform) -> Unit,
) : JBPanel<JBPanel<*>>() {

  init {
    layout = BorderLayout()
    border = JBUI.Borders.empty(20)

    setupUI()
  }

  private fun setupUI() {
    val mainPanel = JBPanel<JBPanel<*>>()
    mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

    // Title
    val titleLabel = JBLabel("<html><h1>Choose Your Platform</h1></html>")
    titleLabel.alignmentX = 0.5f
    mainPanel.add(titleLabel)
    mainPanel.add(Box.createVerticalStrut(10))

    // Subtitle
    val subtitleLabel =
      JBLabel(
        "<html><center>Select the platform you want to integrate RevenueCat SDK with</center></html>",
      )
    subtitleLabel.alignmentX = 0.5f
    mainPanel.add(subtitleLabel)
    mainPanel.add(Box.createVerticalStrut(20))

    // SDK Integration section title
    val sdkSectionLabel = JBLabel("<html><b>SDK Integration</b></html>")
    sdkSectionLabel.alignmentX = 0.5f
    mainPanel.add(sdkSectionLabel)
    mainPanel.add(Box.createVerticalStrut(10))

    // Platform cards container (SDK integration)
    val sdkCardsPanel = JBPanel<JBPanel<*>>()
    sdkCardsPanel.layout = BoxLayout(sdkCardsPanel, BoxLayout.X_AXIS)
    sdkCardsPanel.alignmentX = 0.5f

    // Android Card
    val androidCard = createPlatformCard(
      SdkIntegrationWizard.Platform.ANDROID,
      "Android",
      "Kotlin",
      listOf(
        "Native Android apps",
        "Google Play Billing",
        "Compose integration",
      ),
      false,
    )

    // Kotlin Multiplatform Card
    val kotlinCard = createPlatformCard(
      SdkIntegrationWizard.Platform.KOTLIN,
      "Kotlin Multiplatform",
      "Cross-platform",
      listOf(
        "iOS and Android",
        "Shared business logic",
        "Compose Multiplatform",
      ),
      false,
    )

    // Flutter Card
    val flutterCard = createPlatformCard(
      SdkIntegrationWizard.Platform.FLUTTER,
      "Flutter",
      "Cross-platform",
      listOf(
        "iOS and Android",
        "Single codebase",
        "Dart language",
      ),
      false,
    )

    sdkCardsPanel.add(Box.createHorizontalGlue())
    sdkCardsPanel.add(androidCard)
    sdkCardsPanel.add(Box.createHorizontalStrut(15))
    sdkCardsPanel.add(kotlinCard)
    sdkCardsPanel.add(Box.createHorizontalStrut(15))
    sdkCardsPanel.add(flutterCard)
    sdkCardsPanel.add(Box.createHorizontalGlue())

    mainPanel.add(sdkCardsPanel)
    mainPanel.add(Box.createVerticalStrut(20))

    // Paywall Only section title
    val paywallSectionLabel =
      JBLabel("<html><b>Paywall Only</b> <small>(already have SDK installed)</small></html>")
    paywallSectionLabel.alignmentX = 0.5f
    mainPanel.add(paywallSectionLabel)
    mainPanel.add(Box.createVerticalStrut(10))

    // Paywall cards container
    val paywallCardsPanel = JBPanel<JBPanel<*>>()
    paywallCardsPanel.layout = BoxLayout(paywallCardsPanel, BoxLayout.X_AXIS)
    paywallCardsPanel.alignmentX = 0.5f

    // Android Paywall Card
    val androidPaywallCard = createPlatformCard(
      SdkIntegrationWizard.Platform.ANDROID_PAYWALL,
      "Android Paywall",
      "Add Paywall UI",
      listOf(
        "purchases-ui",
        "Paywall templates",
        "Compose integration",
      ),
      true,
    )

    // KMP Paywall Card
    val kmpPaywallCard = createPlatformCard(
      SdkIntegrationWizard.Platform.KOTLIN_PAYWALL,
      "KMP Paywall",
      "Add Paywall UI",
      listOf(
        "purchases-kmp-ui",
        "Compose Multiplatform",
        "iOS and Android",
      ),
      true,
    )

    // Flutter Paywall Card
    val flutterPaywallCard = createPlatformCard(
      SdkIntegrationWizard.Platform.FLUTTER_PAYWALL,
      "Flutter Paywall",
      "Add Paywall UI",
      listOf(
        "purchases_ui_flutter",
        "Paywall templates",
        "iOS and Android",
      ),
      true,
    )

    paywallCardsPanel.add(Box.createHorizontalGlue())
    paywallCardsPanel.add(androidPaywallCard)
    paywallCardsPanel.add(Box.createHorizontalStrut(15))
    paywallCardsPanel.add(kmpPaywallCard)
    paywallCardsPanel.add(Box.createHorizontalStrut(15))
    paywallCardsPanel.add(flutterPaywallCard)
    paywallCardsPanel.add(Box.createHorizontalGlue())

    mainPanel.add(paywallCardsPanel)
    mainPanel.add(Box.createVerticalGlue())

    val scrollPane = JBScrollPane(mainPanel)
    scrollPane.border = JBUI.Borders.empty()
    add(scrollPane, BorderLayout.CENTER)
  }

  private fun createPlatformCard(
    platform: SdkIntegrationWizard.Platform,
    title: String,
    subtitle: String,
    features: List<String>,
    isPaywallOnly: Boolean,
  ): JPanel {
    val card = JBPanel<JBPanel<*>>()
    card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
    card.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(JBUI.CurrentTheme.Button.buttonOutlineColorStart(false), 1),
      JBUI.Borders.empty(15),
    )
    card.preferredSize = Dimension(200, 240)
    card.maximumSize = Dimension(200, 240)
    card.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    // Logo/Icon panel
    val iconPanel = createCombinedIcon(platform, isPaywallOnly)
    iconPanel.alignmentX = 0.5f
    card.add(iconPanel)
    card.add(Box.createVerticalStrut(10))

    // Title - centered
    val titleLabel = JBLabel("<html><center><b>$title</b></center></html>")
    titleLabel.alignmentX = 0.5f
    titleLabel.horizontalAlignment = SwingConstants.CENTER
    card.add(titleLabel)

    // Subtitle - centered
    val subtitleLabel = JBLabel("<html><center><small>$subtitle</small></center></html>")
    subtitleLabel.font = subtitleLabel.font.deriveFont(Font.ITALIC)
    subtitleLabel.foreground = JBColor.GRAY
    subtitleLabel.alignmentX = 0.5f
    subtitleLabel.horizontalAlignment = SwingConstants.CENTER
    card.add(subtitleLabel)
    card.add(Box.createVerticalStrut(10))

    // Features - centered
    val featuresPanel = JBPanel<JBPanel<*>>()
    featuresPanel.layout = BoxLayout(featuresPanel, BoxLayout.Y_AXIS)
    featuresPanel.alignmentX = 0.5f

    features.forEach { feature ->
      val featureLabel = JBLabel("<html><center><small>$feature</small></center></html>")
      featureLabel.alignmentX = 0.5f
      featureLabel.horizontalAlignment = SwingConstants.CENTER
      featuresPanel.add(featureLabel)
      featuresPanel.add(Box.createVerticalStrut(2))
    }

    card.add(featuresPanel)
    card.add(Box.createVerticalGlue())

    // Select button
    val selectButton = JButton("Select")
    selectButton.alignmentX = 0.5f
    selectButton.addActionListener {
      onPlatformSelected(platform)
    }
    card.add(selectButton)

    // Make entire card clickable
    card.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent?) {
        onPlatformSelected(platform)
      }

      override fun mouseEntered(e: java.awt.event.MouseEvent?) {
        card.border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(JBUI.CurrentTheme.Button.buttonOutlineColorStart(true), 2),
          JBUI.Borders.empty(15),
        )
      }

      override fun mouseExited(e: java.awt.event.MouseEvent?) {
        card.border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(
            JBUI.CurrentTheme.Button.buttonOutlineColorStart(false),
            1,
          ),
          JBUI.Borders.empty(15),
        )
      }
    })

    return card
  }

  private fun createCombinedIcon(
    platform: SdkIntegrationWizard.Platform,
    isPaywallOnly: Boolean,
  ): JPanel {
    val panelWidth = if (isPaywallOnly) 100 else 100
    val panelHeight = if (isPaywallOnly) 70 else 100
    return object : JPanel() {
      init {
        preferredSize = Dimension(panelWidth, panelHeight)
        maximumSize = Dimension(panelWidth, panelHeight)
        isOpaque = false
        layout = null
      }

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )

        val revenueCatColor = java.awt.Color(0xF2, 0x54, 0x5B)

        // Load platform logo
        val logoFileName = when (platform) {
          SdkIntegrationWizard.Platform.ANDROID, SdkIntegrationWizard.Platform.ANDROID_PAYWALL -> "android.png"
          SdkIntegrationWizard.Platform.KOTLIN, SdkIntegrationWizard.Platform.KOTLIN_PAYWALL -> "kotlin.png"
          SdkIntegrationWizard.Platform.FLUTTER, SdkIntegrationWizard.Platform.FLUTTER_PAYWALL -> "flutter.png"
        }

        try {
          val logoStream = javaClass.getResourceAsStream("/images/$logoFileName")
          if (logoStream != null) {
            val logoImage = ImageIO.read(logoStream)
            val imageWidth = logoImage.width.toDouble()
            val imageHeight = logoImage.height.toDouble()

            if (isPaywallOnly) {
              // Draw smaller platform logo on the left, fitting within bounding box
              val maxSize = 40.0
              val scale = minOf(maxSize / imageWidth, maxSize / imageHeight)
              val scaledWidth = (imageWidth * scale).toInt()
              val scaledHeight = (imageHeight * scale).toInt()
              val scaledLogo = logoImage.getScaledInstance(
                scaledWidth,
                scaledHeight,
                java.awt.Image.SCALE_SMOOTH,
              )
              g2.drawImage(scaledLogo, 0, (height - scaledHeight) / 2, null)

              // Draw paywall icon on the right (phone with paywall)
              val paywallX = scaledWidth + 8
              drawPaywallIcon(g2, paywallX, 10, 38, 50, revenueCatColor)
            } else {
              // Draw platform logo centered, using panel size as constraint
              val maxSize = minOf(width, height).toDouble()
              val scale = minOf(maxSize / imageWidth, maxSize / imageHeight)
              val scaledWidth = (imageWidth * scale).toInt()
              val scaledHeight = (imageHeight * scale).toInt()
              val scaledLogo = logoImage.getScaledInstance(
                scaledWidth,
                scaledHeight,
                java.awt.Image.SCALE_SMOOTH,
              )
              val x = (width - scaledWidth) / 2
              val y = (height - scaledHeight) / 2
              g2.drawImage(scaledLogo, x, y, null)
            }
          }
        } catch (e: Exception) {
          // If image loading fails, draw placeholder
          e.printStackTrace()
        }
      }

      private fun drawPaywallIcon(
        g2: Graphics2D,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        accentColor: java.awt.Color,
      ) {
        val bgColor = if (JBColor.isBright()) {
          java.awt.Color(
            0xF5,
            0xF5,
            0xF5,
          )
        } else {
          java.awt.Color(0x3C, 0x3C, 0x3C)
        }

        // Draw phone outline
        g2.color = bgColor
        g2.fillRoundRect(x, y, w, h, 6, 6)
        g2.color = JBColor.border()
        g2.drawRoundRect(x, y, w - 1, h - 1, 6, 6)

        // Draw paywall content
        g2.color = accentColor.brighter()
        g2.fillRoundRect(x + 3, y + 3, w - 6, h - 6, 4, 4)

        // Draw content lines
        g2.color = java.awt.Color.WHITE
        g2.fillRect(x + 8, y + 10, w - 16, 4)
        g2.fillRoundRect(x + 8, y + 20, w - 16, 8, 2, 2)

        // Draw CTA button
        g2.color = accentColor
        g2.fillRoundRect(x + 8, h + y - 14, w - 16, 8, 2, 2)
      }
    }
  }
}
