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
package com.revenuecat.plugin.onboarding

import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import java.awt.Component
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.SwingUtilities

/**
 * Manages onboarding tooltip guides for the RevenueCat plugin
 */
class OnboardingTooltipManager(
  private val onComplete: () -> Unit,
) {

  data class TooltipStep(
    val title: String,
    val description: String,
    val targetComponentFinder: () -> Component?,
  )

  private var currentStepIndex = 0
  private var currentBalloon: Balloon? = null
  private var steps: List<TooltipStep> = emptyList()

  fun setSteps(steps: List<TooltipStep>) {
    this.steps = steps
  }

  fun startOnboarding() {
    currentStepIndex = 0
    showCurrentStep()
  }

  fun isOnboardingNeeded(): Boolean {
    val settings = RevenueCatSettingsState.getInstance()
    return !settings.hasCompletedOnboarding
  }

  private fun showCurrentStep() {
    if (currentStepIndex >= steps.size) {
      completeOnboarding()
      return
    }

    val step = steps[currentStepIndex]
    val targetComponent = step.targetComponentFinder()

    if (targetComponent == null || !targetComponent.isShowing) {
      // Skip this step if component not found or not visible
      currentStepIndex++
      showCurrentStep()
      return
    }

    showTooltip(step, targetComponent)
  }

  private fun showTooltip(step: TooltipStep, targetComponent: Component) {
    // Dismiss any existing balloon
    currentBalloon?.hide()

    val content = createTooltipContent(step)

    val balloon = JBPopupFactory.getInstance()
      .createBalloonBuilder(content)
      .setFillColor(JBColor(0x3C3F41, 0x3C3F41))
      .setBorderColor(JBColor(0x3574F0, 0x3574F0)) // Default blue color
      .setAnimationCycle(200)
      .setHideOnClickOutside(false)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setCloseButtonEnabled(false)
      .setShadow(true)
      .createBalloon()

    currentBalloon = balloon

    // Show balloon below the target component
    SwingUtilities.invokeLater {
      if (targetComponent.isShowing) {
        val point = Point(targetComponent.width / 2, targetComponent.height)
        balloon.show(RelativePoint(targetComponent, point), Balloon.Position.below)
      }
    }
  }

  private fun createTooltipContent(step: TooltipStep): JBPanel<JBPanel<*>> {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(12)
    panel.background = JBColor(0x3C3F41, 0x3C3F41)

    // Step indicator
    val stepIndicator = JBLabel("Step ${currentStepIndex + 1} of ${steps.size}")
    stepIndicator.foreground = JBColor.GRAY
    stepIndicator.font = stepIndicator.font.deriveFont(10f)
    stepIndicator.alignmentX = Component.LEFT_ALIGNMENT
    panel.add(stepIndicator)
    panel.add(javax.swing.Box.createVerticalStrut(5))

    // Title - using HTML with white color to ensure visibility
    val titleLabel = JBLabel("<html><b style='color: #FFFFFF;'>${step.title}</b></html>")
    titleLabel.foreground = java.awt.Color.WHITE
    titleLabel.font = titleLabel.font.deriveFont(14f)
    titleLabel.alignmentX = Component.LEFT_ALIGNMENT
    panel.add(titleLabel)
    panel.add(javax.swing.Box.createVerticalStrut(8))

    // Description
    val descLabel = JBLabel("<html><div style='width: 220px;'>${step.description}</div></html>")
    descLabel.foreground = JBColor(0xBBBBBB, 0xBBBBBB)
    descLabel.font = descLabel.font.deriveFont(12f)
    descLabel.alignmentX = Component.LEFT_ALIGNMENT
    panel.add(descLabel)
    panel.add(javax.swing.Box.createVerticalStrut(12))

    // Buttons row
    val buttonsPanel = JBPanel<JBPanel<*>>()
    buttonsPanel.layout = BoxLayout(buttonsPanel, BoxLayout.X_AXIS)
    buttonsPanel.isOpaque = false
    buttonsPanel.alignmentX = Component.LEFT_ALIGNMENT

    // Skip button
    val skipLabel = JBLabel("Skip tour")
    skipLabel.foreground = JBColor.GRAY
    skipLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    skipLabel.font = skipLabel.font.deriveFont(11f)
    skipLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        skipOnboarding()
      }
      override fun mouseEntered(e: MouseEvent?) {
        skipLabel.foreground = JBColor.WHITE
      }
      override fun mouseExited(e: MouseEvent?) {
        skipLabel.foreground = JBColor.GRAY
      }
    })
    buttonsPanel.add(skipLabel)

    buttonsPanel.add(javax.swing.Box.createHorizontalGlue())

    // Next/Finish button with blue styling
    val isLastStep = currentStepIndex == steps.size - 1
    val nextButton = JButton(if (isLastStep) "Finish" else "Next")
    nextButton.background = java.awt.Color(0x35, 0x74, 0xF0) // Blue color
    nextButton.foreground = java.awt.Color.WHITE
    nextButton.isOpaque = true
    nextButton.isBorderPainted = false
    nextButton.addActionListener {
      nextStep()
    }
    buttonsPanel.add(nextButton)

    panel.add(buttonsPanel)

    return panel
  }

  private fun nextStep() {
    currentBalloon?.hide()
    currentStepIndex++
    showCurrentStep()
  }

  private fun skipOnboarding() {
    currentBalloon?.hide()
    completeOnboarding()
  }

  private fun completeOnboarding() {
    currentBalloon?.hide()
    currentBalloon = null

    val settings = RevenueCatSettingsState.getInstance()
    settings.hasCompletedOnboarding = true

    onComplete()
  }

  fun dismissCurrentTooltip() {
    currentBalloon?.hide()
    currentBalloon = null
  }
}
