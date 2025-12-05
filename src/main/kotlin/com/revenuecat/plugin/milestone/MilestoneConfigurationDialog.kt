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
package com.revenuecat.plugin.milestone

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for configuring revenue and user milestones
 */
class MilestoneConfigurationDialog(private val project: Project) : DialogWrapper(project) {

  private val settings = RevenueCatSettingsState.getInstance()

  // MRR
  private val mrrEnabledCheckbox = JBCheckBox("Track MRR", settings.mrrMilestoneEnabled)
  private val mrrMilestoneField = JBTextField(15)

  // Active Subscriptions
  private val activeSubscriptionsEnabledCheckbox =
    JBCheckBox("Track Active Subscriptions", settings.activeSubscriptionsMilestoneEnabled)
  private val activeSubscriptionsMilestoneField = JBTextField(15)

  // Active Trials
  private val activeTrialsEnabledCheckbox =
    JBCheckBox("Track Active Trials", settings.activeTrialsMilestoneEnabled)
  private val activeTrialsMilestoneField = JBTextField(15)

  // Active Users
  private val activeUsersEnabledCheckbox =
    JBCheckBox("Track Active Users", settings.activeUsersMilestoneEnabled)
  private val activeUsersMilestoneField = JBTextField(15)

  // New Customers
  private val newCustomersEnabledCheckbox =
    JBCheckBox("Track New Customers", settings.newCustomersMilestoneEnabled)
  private val newCustomersMilestoneField = JBTextField(15)

  // Revenue
  private val revenueEnabledCheckbox =
    JBCheckBox("Track Total Revenue", settings.revenueMilestoneEnabled)
  private val revenueMilestoneField = JBTextField(15)

  // Global notifications
  private val enableNotificationsCheckbox =
    JBCheckBox("Enable milestone notifications", settings.milestoneNotificationsEnabled)

  init {
    title = "Configure Milestones"

    // Load current values
    mrrMilestoneField.text = if (settings.mrrMilestone > 0.0) settings.mrrMilestone.toString() else ""
    activeSubscriptionsMilestoneField.text = if (settings.activeSubscriptionsMilestone > 0) settings.activeSubscriptionsMilestone.toString() else ""
    activeTrialsMilestoneField.text = if (settings.activeTrialsMilestone > 0) settings.activeTrialsMilestone.toString() else ""
    activeUsersMilestoneField.text = if (settings.activeUsersMilestone > 0) settings.activeUsersMilestone.toString() else ""
    newCustomersMilestoneField.text = if (settings.newCustomersMilestone > 0) settings.newCustomersMilestone.toString() else ""
    revenueMilestoneField.text = if (settings.revenueMilestone > 0.0) settings.revenueMilestone.toString() else ""

    init()
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = JBPanel<JBPanel<*>>()
    mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
    mainPanel.border = JBUI.Borders.empty(10)

    // Title
    val titleLabel = JBLabel("<html><b>Set your milestone targets:</b></html>")
    titleLabel.border = JBUI.Borders.emptyBottom(10)
    mainPanel.add(titleLabel)

    // MRR
    mainPanel.add(
      createMilestoneRow(
        mrrEnabledCheckbox,
        "MRR ($):",
        mrrMilestoneField,
        String.format("$%.2f", settings.currentMrr),
      ),
    )

    // Active Subscriptions
    mainPanel.add(
      createMilestoneRow(
        activeSubscriptionsEnabledCheckbox,
        "Active Subscriptions:",
        activeSubscriptionsMilestoneField,
        settings.currentActiveSubscriptions.toString(),
      ),
    )

    // Active Trials
    mainPanel.add(
      createMilestoneRow(
        activeTrialsEnabledCheckbox,
        "Active Trials:",
        activeTrialsMilestoneField,
        settings.currentActiveTrials.toString(),
      ),
    )

    // Active Users
    mainPanel.add(
      createMilestoneRow(
        activeUsersEnabledCheckbox,
        "Active Users:",
        activeUsersMilestoneField,
        settings.currentActiveUsers.toString(),
      ),
    )

    // New Customers
    mainPanel.add(
      createMilestoneRow(
        newCustomersEnabledCheckbox,
        "New Customers:",
        newCustomersMilestoneField,
        settings.currentNewCustomers.toString(),
      ),
    )

    // Revenue
    mainPanel.add(
      createMilestoneRow(
        revenueEnabledCheckbox,
        "Total Revenue ($):",
        revenueMilestoneField,
        String.format("$%.2f", settings.currentRevenue),
      ),
    )

    // Global notifications toggle
    val notifPanel = JBPanel<JBPanel<*>>()
    notifPanel.border = JBUI.Borders.emptyTop(10)
    notifPanel.add(enableNotificationsCheckbox)
    mainPanel.add(notifPanel)

    val scrollPane = JBScrollPane(mainPanel)
    scrollPane.border = JBUI.Borders.empty()
    return scrollPane
  }

  private fun createMilestoneRow(
    checkbox: JBCheckBox,
    label: String,
    textField: JBTextField,
    currentValue: String,
  ): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = GridBagLayout()
    panel.border = JBUI.Borders.emptyBottom(10)

    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.anchor = GridBagConstraints.WEST
    gbc.gridwidth = 3
    gbc.insets = JBUI.insets(0, 0, 5, 0)

    // Checkbox
    panel.add(checkbox, gbc)

    // Label
    gbc.gridy = 1
    gbc.gridwidth = 1
    gbc.insets = JBUI.insets(0, 20, 0, 5)
    panel.add(JBLabel(label), gbc)

    // Text field
    gbc.gridx = 1
    gbc.fill = GridBagConstraints.HORIZONTAL
    gbc.weightx = 0.5
    gbc.insets = JBUI.insets(0, 0, 0, 10)
    panel.add(textField, gbc)

    // Current value
    gbc.gridx = 2
    gbc.fill = GridBagConstraints.NONE
    gbc.weightx = 0.0
    gbc.insets = JBUI.insets(0)
    panel.add(JBLabel("<html><i>Current: $currentValue</i></html>"), gbc)

    return panel
  }

  override fun doOKAction() {
    // Validate and save settings
    try {
      // MRR
      settings.mrrMilestoneEnabled = mrrEnabledCheckbox.isSelected
      val mrrValue = mrrMilestoneField.text.trim()
      settings.mrrMilestone = if (mrrValue.isNotBlank()) mrrValue.toDouble() else 0.0

      // Active Subscriptions
      settings.activeSubscriptionsMilestoneEnabled = activeSubscriptionsEnabledCheckbox.isSelected
      val activeSubsValue = activeSubscriptionsMilestoneField.text.trim()
      settings.activeSubscriptionsMilestone = if (activeSubsValue.isNotBlank()) activeSubsValue.toInt() else 0

      // Active Trials
      settings.activeTrialsMilestoneEnabled = activeTrialsEnabledCheckbox.isSelected
      val activeTrialsValue = activeTrialsMilestoneField.text.trim()
      settings.activeTrialsMilestone = if (activeTrialsValue.isNotBlank()) activeTrialsValue.toInt() else 0

      // Active Users
      settings.activeUsersMilestoneEnabled = activeUsersEnabledCheckbox.isSelected
      val activeUsersValue = activeUsersMilestoneField.text.trim()
      settings.activeUsersMilestone = if (activeUsersValue.isNotBlank()) activeUsersValue.toInt() else 0

      // New Customers
      settings.newCustomersMilestoneEnabled = newCustomersEnabledCheckbox.isSelected
      val newCustomersValue = newCustomersMilestoneField.text.trim()
      settings.newCustomersMilestone = if (newCustomersValue.isNotBlank()) newCustomersValue.toInt() else 0

      // Revenue
      settings.revenueMilestoneEnabled = revenueEnabledCheckbox.isSelected
      val revenueValue = revenueMilestoneField.text.trim()
      settings.revenueMilestone = if (revenueValue.isNotBlank()) revenueValue.toDouble() else 0.0

      settings.milestoneNotificationsEnabled = enableNotificationsCheckbox.isSelected

      super.doOKAction()
    } catch (e: NumberFormatException) {
      // Invalid number format - show error or just ignore
      super.doOKAction()
    }
  }
}
