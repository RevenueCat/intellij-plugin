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
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Panel that asks whether the user wants to set up Paywall UI
 */
class PaywallOptionPanel(
  private val onOptionSelected: (Boolean) -> Unit,
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
    val titleLabel = JBLabel("<html><h1>Set Up Paywall UI?</h1></html>")
    titleLabel.alignmentX = 0.5f
    mainPanel.add(titleLabel)
    mainPanel.add(Box.createVerticalStrut(10))

    // Subtitle
    val subtitleLabel =
      JBLabel(
        "<html><center>RevenueCat Paywalls make it easy to display beautiful, remotely configurable paywall screens in your app.</center></html>",
      )
    subtitleLabel.alignmentX = 0.5f
    mainPanel.add(subtitleLabel)
    mainPanel.add(Box.createVerticalStrut(30))

    // Option cards container
    val cardsPanel = JBPanel<JBPanel<*>>()
    cardsPanel.layout = BoxLayout(cardsPanel, BoxLayout.X_AXIS)
    cardsPanel.alignmentX = 0.5f

    // Yes - Set up Paywall Card
    val yesCard = createOptionCard(
      true,
      "Yes, set up Paywall UI",
      listOf(
        "Add purchases-ui dependency",
        "Generate paywall code templates",
        "Choose from multiple paywall styles",
      ),
      true,
    )

    // No - Skip Paywall Card
    val noCard = createOptionCard(
      false,
      "No, skip for now",
      listOf(
        "Complete SDK setup only",
        "Set up paywall later",
        "Use custom purchase UI",
      ),
      false,
    )

    cardsPanel.add(Box.createHorizontalGlue())
    cardsPanel.add(yesCard)
    cardsPanel.add(Box.createHorizontalStrut(30))
    cardsPanel.add(noCard)
    cardsPanel.add(Box.createHorizontalGlue())

    mainPanel.add(cardsPanel)
    mainPanel.add(Box.createVerticalStrut(30))

    // Info section
    val infoLabel =
      JBLabel(
        "<html><center><small>You can always add Paywall UI later by running this wizard again.</small></center></html>",
      )
    infoLabel.foreground = JBColor.GRAY
    infoLabel.alignmentX = 0.5f
    mainPanel.add(infoLabel)

    mainPanel.add(Box.createVerticalGlue())

    add(mainPanel, BorderLayout.CENTER)
  }

  private fun createOptionCard(
    setupPaywall: Boolean,
    title: String,
    features: List<String>,
    isPrimary: Boolean,
  ): JPanel {
    val card = JBPanel<JBPanel<*>>()
    card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
    card.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(
        if (isPrimary) {
          java.awt.Color(
            0xF2,
            0x54,
            0x5B,
          )
        } else {
          JBUI.CurrentTheme.Button.buttonOutlineColorStart(false)
        },
        if (isPrimary) 2 else 1,
      ),
      JBUI.Borders.empty(20),
    )
    card.preferredSize = Dimension(250, 280)
    card.maximumSize = Dimension(250, 280)
    card.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    // Icon preview
    val iconPanel = createIconPreview(setupPaywall)
    iconPanel.alignmentX = 0.5f
    card.add(iconPanel)
    card.add(Box.createVerticalStrut(15))

    // Title
    val titleLabel = JBLabel("<html><b>$title</b></html>")
    titleLabel.alignmentX = 0.5f
    card.add(titleLabel)
    card.add(Box.createVerticalStrut(15))

    // Features
    val featuresPanel = JBPanel<JBPanel<*>>()
    featuresPanel.layout = BoxLayout(featuresPanel, BoxLayout.Y_AXIS)
    featuresPanel.alignmentX = 0.5f

    features.forEach { feature ->
      val featureLabel = JBLabel("<html><small>• $feature</small></html>")
      featureLabel.alignmentX = 0.0f
      featuresPanel.add(featureLabel)
      featuresPanel.add(Box.createVerticalStrut(3))
    }

    card.add(featuresPanel)
    card.add(Box.createVerticalGlue())

    // Select button
    val selectButton = JButton(if (setupPaywall) "Set Up Paywall" else "Skip")
    if (isPrimary) {
      selectButton.background = java.awt.Color(0xF2, 0x54, 0x5B)
      selectButton.foreground = java.awt.Color.WHITE
    }
    selectButton.alignmentX = 0.5f
    selectButton.addActionListener {
      onOptionSelected(setupPaywall)
    }
    card.add(selectButton)

    // Make entire card clickable
    card.addMouseListener(object : java.awt.event.MouseAdapter() {
      override fun mouseClicked(e: java.awt.event.MouseEvent?) {
        onOptionSelected(setupPaywall)
      }

      override fun mouseEntered(e: java.awt.event.MouseEvent?) {
        card.border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(
            if (isPrimary) {
              java.awt.Color(
                0xF2,
                0x54,
                0x5B,
              )
            } else {
              JBUI.CurrentTheme.Button.buttonOutlineColorStart(true)
            },
            2,
          ),
          JBUI.Borders.empty(20),
        )
      }

      override fun mouseExited(e: java.awt.event.MouseEvent?) {
        card.border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(
            if (isPrimary) {
              java.awt.Color(
                0xF2,
                0x54,
                0x5B,
              )
            } else {
              JBUI.CurrentTheme.Button.buttonOutlineColorStart(false)
            },
            if (isPrimary) 2 else 1,
          ),
          JBUI.Borders.empty(20),
        )
      }
    })

    return card
  }

  private fun createIconPreview(setupPaywall: Boolean): JPanel {
    return object : JPanel() {
      init {
        preferredSize = Dimension(80, 80)
        maximumSize = Dimension(80, 80)
        isOpaque = false
      }

      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val revenueCatColor = java.awt.Color(0xF2, 0x54, 0x5B)
        val bgColor = if (JBColor.isBright()) {
          java.awt.Color(
            0xF5,
            0xF5,
            0xF5,
          )
        } else {
          java.awt.Color(0x3C, 0x3C, 0x3C)
        }

        if (setupPaywall) {
          // Draw paywall icon (phone with paywall)
          g2.color = bgColor
          g2.fillRoundRect(15, 0, 50, 80, 8, 8)
          g2.color = JBColor.border()
          g2.drawRoundRect(15, 0, 50, 80, 8, 8)

          // Paywall content
          g2.color = revenueCatColor.brighter()
          g2.fillRoundRect(20, 5, 40, 70, 5, 5)

          g2.color = java.awt.Color.WHITE
          g2.fillRect(25, 15, 30, 6)
          g2.fillRoundRect(25, 30, 30, 12, 3, 3)
          g2.fillRoundRect(25, 45, 30, 12, 3, 3)

          g2.color = revenueCatColor
          g2.fillRoundRect(25, 62, 30, 10, 3, 3)
        } else {
          // Draw skip icon (checkmark)
          g2.color = bgColor
          g2.fillOval(10, 10, 60, 60)
          g2.color = JBColor.border()
          g2.drawOval(10, 10, 60, 60)

          // Arrow pointing right
          g2.color = JBColor.GRAY
          g2.stroke = java.awt.BasicStroke(3f)
          g2.drawLine(30, 40, 50, 40)
          g2.drawLine(45, 32, 50, 40)
          g2.drawLine(45, 48, 50, 40)
        }
      }
    }
  }
}
