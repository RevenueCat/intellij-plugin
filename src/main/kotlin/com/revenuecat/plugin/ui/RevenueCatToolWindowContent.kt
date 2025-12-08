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
package com.revenuecat.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.revenuecat.plugin.ai.AIChatPanel
import com.revenuecat.plugin.api.models.RevenueSummary
import com.revenuecat.plugin.api.models.Subscription
import com.revenuecat.plugin.onboarding.OnboardingTooltipManager
import com.revenuecat.plugin.services.RevenueCatApiService
import com.revenuecat.plugin.settings.RevenueCatSettingsConfigurable
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import com.revenuecat.plugin.wizard.BlogArticlesDialog
import com.revenuecat.plugin.wizard.ReleaseNotesDialog
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.GridLayout
import java.awt.Image
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.random.Random

private const val UTM_PARAMS =
  "utm_medium=organic&utm_source=intellij_plugin&utm_campaign=advocate"

/**
 * Main content panel for the RevenueCat tool window
 */
class RevenueCatToolWindowContent(private val project: Project) {

  private val mainPanel = SimpleToolWindowPanel(true, true)
  private val contentPanel = JBPanel<JBPanel<*>>()
  private val apiService = RevenueCatApiService.getInstance()

  // AI Assistant panel
  private val aiChatPanel = AIChatPanel()
  private var isAIPanelVisible = false
  private val splitter = JBSplitter(false, 0.35f)
  private val mainScrollPane = JBScrollPane(contentPanel)

  // Toolbar component reference for onboarding
  private var toolbarComponent: JComponent? = null

  // Reference to AI action for toggle state
  private var aiAssistantAction: AIAssistantAction? = null

  // Onboarding manager
  private val onboardingManager = OnboardingTooltipManager {
    // Called when onboarding completes
  }

  init {
    setupUI()
    loadData()
    // Start onboarding after a short delay to ensure UI is rendered
    SwingUtilities.invokeLater {
      Timer(500) {
        if (onboardingManager.isOnboardingNeeded()) {
          startOnboarding()
        }
      }.apply {
        isRepeats = false
        start()
      }
    }
  }

  private fun setupUI() {
    contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
    contentPanel.border = JBUI.Borders.empty(10)

    mainScrollPane.border = JBUI.Borders.empty()

    // Setup splitter with AI panel on left, main content on right
    splitter.firstComponent = null // AI panel hidden initially
    splitter.secondComponent = mainScrollPane
    splitter.dividerWidth = 3

    mainPanel.toolbar = createToolbar()
    mainPanel.setContent(splitter)

    // Set minimum width for the tool window
    mainPanel.minimumSize = java.awt.Dimension(350, 0)
  }

  /**
   * Toggle the AI Assistant panel visibility
   */
  private fun toggleAIPanel() {
    isAIPanelVisible = !isAIPanelVisible

    if (isAIPanelVisible) {
      // Wrap AI panel in a scroll pane with border
      val aiPanelWrapper = JBPanel<JBPanel<*>>(BorderLayout())
      aiPanelWrapper.border = BorderFactory.createMatteBorder(0, 0, 0, 1, JBColor.border())
      aiPanelWrapper.add(aiChatPanel, BorderLayout.CENTER)
      aiPanelWrapper.minimumSize = java.awt.Dimension(250, 0)

      splitter.firstComponent = aiPanelWrapper
      splitter.proportion = 0.4f
    } else {
      splitter.firstComponent = null
    }

    // Update the action's toggle state
    aiAssistantAction?.updateToggleState(isAIPanelVisible)

    mainPanel.revalidate()
    mainPanel.repaint()
  }

  private fun createToolbar(): JComponent {
    val actionGroup = DefaultActionGroup()
    actionGroup.add(RefreshAction())
    actionGroup.add(SettingsAction())
    actionGroup.add(ShareAction())
    actionGroup.addSeparator()
    actionGroup.add(SdkSetupWizardAction())
    actionGroup.add(MilestoneAction())
    actionGroup.add(ReleaseNotesAction())
    actionGroup.add(BlogAction())
    actionGroup.addSeparator()
    aiAssistantAction = AIAssistantAction()
    actionGroup.add(aiAssistantAction!!)

    val toolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true)
    toolbar.targetComponent = contentPanel

    toolbarComponent = toolbar.component
    return toolbar.component
  }

  /**
   * Start the onboarding tooltip tour
   */
  fun startOnboarding() {
    val toolbar = toolbarComponent ?: return

    // Find toolbar button components by iterating through the toolbar
    val toolbarButtons = mutableListOf<java.awt.Component>()
    findButtons(toolbar, toolbarButtons)

    // Define onboarding steps - map to toolbar buttons by index
    // Order: Refresh(0), Settings(1), [separator], SDK Setup(2), Milestones(3), Release Notes(4), Blog(5)
    val steps = mutableListOf<OnboardingTooltipManager.TooltipStep>()

    // Settings button (index 1)
    steps.add(
      OnboardingTooltipManager.TooltipStep(
        title = "Settings",
        description = "Configure your RevenueCat API credentials, " +
          "SDK API key, and other plugin settings here.",
        targetComponentFinder = { findToolbarButton(1) },
      ),
    )

    // SDK Setup Wizard button (index 2, after separator)
    steps.add(
      OnboardingTooltipManager.TooltipStep(
        title = "SDK Setup Wizard",
        description = "Quickly integrate RevenueCat SDK into your Android, " +
          "Kotlin Multiplatform, or Flutter project with step-by-step guidance.",
        targetComponentFinder = { findToolbarButton(2) },
      ),
    )

    // Milestones button (index 3)
    steps.add(
      OnboardingTooltipManager.TooltipStep(
        title = "Milestones",
        description = "Set revenue and user goals to track your progress. " +
          "Get notified with a celebration when you hit your targets!",
        targetComponentFinder = { findToolbarButton(3) },
      ),
    )

    // Release Notes button (index 4)
    steps.add(
      OnboardingTooltipManager.TooltipStep(
        title = "Release Notes",
        description = "Stay updated with the latest RevenueCat SDK releases. " +
          "Enable notifications to know when new versions are available.",
        targetComponentFinder = { findToolbarButton(4) },
      ),
    )

    // Blog button (index 5)
    steps.add(
      OnboardingTooltipManager.TooltipStep(
        title = "Blog Posts",
        description = "Read the latest articles from RevenueCat blog. " +
          "Enable notifications to get updates on new posts.",
        targetComponentFinder = { findToolbarButton(5) },
      ),
    )

    // AI Assistant button (index 6)
    steps.add(
      OnboardingTooltipManager.TooltipStep(
        title = "AI Assistant",
        description = "Chat with the AI assistant to query metrics, " +
          "get help with RevenueCat integration, and more.",
        targetComponentFinder = { findToolbarButton(6) },
      ),
    )

    onboardingManager.setSteps(steps)
    onboardingManager.startOnboarding()
  }

  private fun findToolbarButton(index: Int): java.awt.Component? {
    val toolbar = toolbarComponent ?: return null
    val buttons = mutableListOf<java.awt.Component>()
    findButtons(toolbar, buttons)
    return buttons.getOrNull(index)
  }

  private fun findButtons(container: java.awt.Container, buttons: MutableList<java.awt.Component>) {
    for (component in container.components) {
      // ActionButton is the class used for toolbar buttons
      if (component.javaClass.simpleName.contains("ActionButton")) {
        buttons.add(component)
      } else if (component is java.awt.Container) {
        findButtons(component, buttons)
      }
    }
  }

  private fun createShippyImageLabel(): JLabel? {
    return try {
      // List of shippy images in resources
      val imageNames = listOf(
        "/images/shippy_1.png",
        "/images/shippy_2.png",
        "/images/shippy_3.png",
        "/images/shippy_4.png",
      )

      // Pick a random image
      val randomImagePath = imageNames[Random.nextInt(imageNames.size)]
      val imageUrl = javaClass.getResource(randomImagePath)
        ?: return null

      val bufferedImage = ImageIO.read(imageUrl)

      // Scale image with smaller width while maintaining aspect ratio (80px width)
      val targetWidth = 80
      val originalWidth = bufferedImage.width
      val originalHeight = bufferedImage.height
      val aspectRatio = originalHeight.toDouble() / originalWidth.toDouble()
      val targetHeight = (targetWidth * aspectRatio).toInt()

      val scaledImage =
        bufferedImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
      val icon = ImageIcon(scaledImage)

      JLabel(icon)
    } catch (e: Exception) {
      println("RevenueCat Tool Window: Failed to load shippy image: ${e.message}")
      null
    }
  }

  private fun loadData() {
    contentPanel.removeAll()

    val settings = RevenueCatSettingsState.getInstance()

    if (!settings.isConfigured()) {
      showNotConfigured()
      return
    }

    showLoading()

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        println("RevenueCat Tool Window: Starting metrics fetch...")
        val summaryResult = apiService.calculateRevenueSummary()
        val resultMsg = if (summaryResult.isSuccess) {
          "SUCCESS"
        } else {
          "FAILED - ${summaryResult.exceptionOrNull()?.message}"
        }
        println("RevenueCat Tool Window: Summary result: $resultMsg")

        // Fetch project name
        val projectNameResult = try {
          apiService.getProjectName(settings.projectId)
        } catch (e: Exception) {
          println("RevenueCat Tool Window: Project name fetch failed: ${e.message}")
          Result.success(settings.projectId)
        }

        // Fetch offerings
        val offeringsResult = try {
          apiService.fetchOfferings()
        } catch (e: Exception) {
          println("RevenueCat Tool Window: Offerings fetch failed: ${e.message}")
          Result.failure(e)
        }

        // Fetch subscriber offerings with paywall data
        val subscriberOfferingsResult = try {
          apiService.fetchSubscriberOfferings()
        } catch (e: Exception) {
          Result.failure(e)
        }

        val projectName = projectNameResult.getOrDefault(settings.projectId)

        SwingUtilities.invokeLater {
          contentPanel.removeAll()

          if (summaryResult.isSuccess) {
            println("RevenueCat Tool Window: Showing dashboard")
            showDashboard(
              summaryResult.getOrNull()!!,
              offeringsResult,
              subscriberOfferingsResult,
              projectName,
            )
          } else {
            val error = summaryResult.exceptionOrNull()
            println("RevenueCat Tool Window: Showing error: ${error?.message}")
            showError(error?.message ?: "Unknown error")
          }

          contentPanel.revalidate()
          contentPanel.repaint()
        }
      } catch (e: Exception) {
        println("RevenueCat Tool Window: EXCEPTION caught: ${e.message}")
        e.printStackTrace()

        SwingUtilities.invokeLater {
          contentPanel.removeAll()
          showError("Unexpected error: ${e.message}\n\nPlease check the IDE logs for more details.")
          contentPanel.revalidate()
          contentPanel.repaint()
        }
      }
    }
  }

  private fun showNotConfigured() {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(20)
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 480)

    // Title
    val titleLabel = JBLabel("<html><h2>Welcome to RevenueCat Plugin!</h2></html>")
    titleLabel.alignmentX = 0.0f
    panel.add(titleLabel)
    panel.add(Box.createVerticalStrut(20))

    // Instructions
    val instructionsLabel = JBLabel(
      "<html>" +
        "<b>Get started with RevenueCat in 3 simple steps:</b><br/><br/>" +
        "<b>Step 1: Login to RevenueCat Dashboard</b><br/>" +
        "• Click the button below to open the RevenueCat dashboard<br/>" +
        "• Sign in to your account (or create a new one if needed)<br/><br/>" +
        "<b>Step 2: Get Your API Credentials</b><br/>" +
        "• Navigate to your project settings<br/>" +
        "• Copy your <b>Secret API Key</b> (starts with 'sk_')<br/>" +
        "• Copy your <b>Project ID</b><br/>" +
        "• Copy your platform-specific <b>SDK API Key</b> " +
        "(starts with 'goog_', 'appl_', etc.)<br/><br/>" +
        "<b>Step 3: Configure the Plugin</b><br/>" +
        "• Click 'Configure Plugin' below<br/>" +
        "• Paste your API credentials<br/>" +
        "</html>",
    )
    instructionsLabel.alignmentX = 0.0f
    panel.add(instructionsLabel)
    panel.add(Box.createVerticalStrut(20))

    // Buttons panel
    val buttonsPanel = JBPanel<JBPanel<*>>()
    buttonsPanel.layout = BoxLayout(buttonsPanel, BoxLayout.X_AXIS)
    buttonsPanel.alignmentX = 0.0f

    val dashboardButton = JButton("Open RevenueCat Dashboard")
    dashboardButton.addActionListener {
      BrowserUtil.browse("https://app.revenuecat.com/overview?$UTM_PARAMS")
    }

    val configButton = JButton("Configure Plugin")
    configButton.addActionListener {
      ShowSettingsUtil.getInstance().showSettingsDialog(
        project,
        RevenueCatSettingsConfigurable::class.java,
      )
    }

    val wizardButton = JButton("SDK Setup Wizard")
    wizardButton.addActionListener {
      val wizard = com.revenuecat.plugin.wizard.SdkIntegrationWizard(project)
      wizard.show()
    }

    buttonsPanel.add(dashboardButton)
    buttonsPanel.add(Box.createHorizontalStrut(10))
    buttonsPanel.add(wizardButton)
    buttonsPanel.add(Box.createHorizontalStrut(10))
    buttonsPanel.add(configButton)
    buttonsPanel.add(Box.createHorizontalGlue())

    panel.add(buttonsPanel)
    panel.add(Box.createVerticalStrut(20))

    // Onboarding guide link
    val tourLabel = JBLabel("<html><a href=''>Take a tour of this plugin</a></html>")
    tourLabel.foreground = JBColor.GRAY
    tourLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    tourLabel.font = tourLabel.font.deriveFont(11f)
    tourLabel.alignmentX = 0.0f
    tourLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        startOnboarding()
      }

      override fun mouseEntered(e: MouseEvent?) {
        tourLabel.foreground = JBColor(0x589DF6, 0x589DF6)
      }

      override fun mouseExited(e: MouseEvent?) {
        tourLabel.foreground = JBColor.GRAY
      }
    })
    panel.add(tourLabel)

    contentPanel.add(panel)
    contentPanel.revalidate()
    contentPanel.repaint()
  }

  private fun showLoading() {
    val label = JBLabel("Loading data from RevenueCat...")
    label.border = JBUI.Borders.empty(10)
    contentPanel.add(label)
    contentPanel.revalidate()
    contentPanel.repaint()
  }

  private fun showError(message: String) {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(10)

    val errorLabel = JBLabel("<html><b>Error:</b></html>")
    errorLabel.foreground = JBColor.RED

    val messageLabel = JBLabel("<html>" + message.replace("\n", "<br/>") + "</html>")
    messageLabel.border = JBUI.Borders.empty(5, 0, 10, 0)

    panel.add(errorLabel)
    panel.add(messageLabel)

    // Add settings button if it's a configuration issue
    if (message.contains(
        "App User ID",
      ) || message.contains("API key") || message.contains("configured")
    ) {
      val settingsButton = JButton("Open Settings")
      settingsButton.addActionListener {
        ShowSettingsUtil.getInstance().showSettingsDialog(
          project,
          RevenueCatSettingsConfigurable::class.java,
        )
      }
      panel.add(settingsButton)
    }

    contentPanel.add(panel)
  }

  private fun createProjectHeader(projectName: String): JPanel {
    val settings = RevenueCatSettingsState.getInstance()
    val projectId = settings.projectId

    // Remove 'proj' prefix for URL if present
    val projectIdForUrl = if (projectId.startsWith("proj")) {
      projectId.removePrefix("proj")
    } else {
      projectId
    }
    val dashboardUrl =
      "https://app.revenuecat.com/projects/$projectIdForUrl/overview?$UTM_PARAMS"

    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(0, 0, 10, 0)
    panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

    // Project name
    val projectLabel = JBLabel(projectName)
    projectLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.add(projectLabel)
    panel.add(Box.createVerticalStrut(5))

    // Dashboard link and Feedback row
    val linkRow = JBPanel<JBPanel<*>>(BorderLayout())
    linkRow.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    linkRow.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 20)

    val linkLabel = JBLabel("<html><a href=''>Open in RevenueCat Dashboard</a></html>")
    linkLabel.foreground = JBColor(0x589DF6, 0x589DF6)
    linkLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    linkLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        BrowserUtil.browse(dashboardUrl)
      }
    })
    linkRow.add(linkLabel, BorderLayout.WEST)

    val feedbackLabel = JBLabel("<html><a href=''>Feedback?</a></html>")
    feedbackLabel.foreground = JBColor.GRAY
    feedbackLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    feedbackLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        BrowserUtil.browse("https://forms.gle/rB8sdKm6CJUZdNNB6")
      }
    })
    linkRow.add(feedbackLabel, BorderLayout.EAST)

    panel.add(linkRow)

    return panel
  }

  private fun createMilestonesPanel(summary: RevenueSummary): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(10)
    panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 600)

    val settings = RevenueCatSettingsState.getInstance()

    // Update current values from summary
    settings.currentMrr = summary.mrr
    settings.currentActiveSubscriptions = summary.activeSubscriptions
    settings.currentActiveTrials = summary.activeTrials
    settings.currentActiveUsers = summary.activeUsers
    settings.currentNewCustomers = summary.newCustomers
    settings.currentRevenue = summary.revenue

    var addedAny = false

    // MRR Milestone
    if (settings.mrrMilestoneEnabled && settings.mrrMilestone > 0.0) {
      if (addedAny) panel.add(Box.createVerticalStrut(15))
      panel.add(
        createMilestoneProgressBar(
          "Monthly Recurring Revenue (MRR)",
          settings.currentMrr,
          settings.mrrMilestone,
          true,
        ),
      )
      addedAny = true
    }

    // Active Subscriptions Milestone
    if (settings.activeSubscriptionsMilestoneEnabled && settings.activeSubscriptionsMilestone > 0) {
      if (addedAny) panel.add(Box.createVerticalStrut(15))
      panel.add(
        createMilestoneProgressBar(
          "Active Subscriptions",
          settings.currentActiveSubscriptions.toDouble(),
          settings.activeSubscriptionsMilestone.toDouble(),
          false,
        ),
      )
      addedAny = true
    }

    // Active Trials Milestone
    if (settings.activeTrialsMilestoneEnabled && settings.activeTrialsMilestone > 0) {
      if (addedAny) panel.add(Box.createVerticalStrut(15))
      panel.add(
        createMilestoneProgressBar(
          "Active Trials",
          settings.currentActiveTrials.toDouble(),
          settings.activeTrialsMilestone.toDouble(),
          false,
        ),
      )
      addedAny = true
    }

    // Active Users Milestone
    if (settings.activeUsersMilestoneEnabled && settings.activeUsersMilestone > 0) {
      if (addedAny) panel.add(Box.createVerticalStrut(15))
      panel.add(
        createMilestoneProgressBar(
          "Active Users",
          settings.currentActiveUsers.toDouble(),
          settings.activeUsersMilestone.toDouble(),
          false,
        ),
      )
      addedAny = true
    }

    // New Customers Milestone
    if (settings.newCustomersMilestoneEnabled && settings.newCustomersMilestone > 0) {
      if (addedAny) panel.add(Box.createVerticalStrut(15))
      panel.add(
        createMilestoneProgressBar(
          "New Customers",
          settings.currentNewCustomers.toDouble(),
          settings.newCustomersMilestone.toDouble(),
          false,
        ),
      )
      addedAny = true
    }

    // Revenue Milestone
    if (settings.revenueMilestoneEnabled && settings.revenueMilestone > 0.0) {
      if (addedAny) panel.add(Box.createVerticalStrut(15))
      panel.add(
        createMilestoneProgressBar(
          "Total Revenue",
          settings.currentRevenue,
          settings.revenueMilestone,
          true,
        ),
      )
      addedAny = true
    }

    return panel
  }

  private fun createMilestoneProgressBar(
    title: String,
    current: Double,
    target: Double,
    isCurrency: Boolean,
  ): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 80)

    // Title and values
    val percentage = if (target > 0.0) ((current / target) * 100).coerceAtMost(100.0) else 0.0
    val currentStr = if (isCurrency) String.format("$%.2f", current) else current.toInt().toString()
    val targetStr = if (isCurrency) String.format("$%.2f", target) else target.toInt().toString()

    val headerLabel = JBLabel("<html><b>$title</b></html>")
    headerLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.add(headerLabel)
    panel.add(Box.createVerticalStrut(5))

    // Progress bar with rounded corners - custom class to maintain color across theme changes
    val progressBar = RevenueCatProgressBar()
    progressBar.value = percentage.toInt()
    progressBar.isStringPainted = true
    progressBar.string = "$currentStr / $targetStr (${String.format("%.1f", percentage)}%)"

    progressBar.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    progressBar.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 25)
    panel.add(progressBar)

    // Check if milestone is reached and show notification
    if (percentage >= 100.0) {
      panel.add(Box.createVerticalStrut(5))
      val achievedLabel = JBLabel("🎉 Milestone achieved!")
      achievedLabel.foreground = JBColor.GREEN
      achievedLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
      panel.add(achievedLabel)

      // Trigger notification
      checkAndNotifyMilestone(title, current, target, isCurrency)

      // Trigger confetti (only once per milestone)
      val milestoneKey = "$title:$target"
      if (!confettiShown.contains(milestoneKey)) {
        confettiShown.add(milestoneKey)
        // Delay slightly to ensure panel is rendered
        SwingUtilities.invokeLater {
          showConfettiAnimation()
        }
      }
    }

    return panel
  }

  // Track which milestones have already been notified to avoid duplicate notifications
  private val notifiedMilestones = mutableSetOf<String>()
  private val confettiShown = mutableSetOf<String>()

  private data class ConfettiPiece(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    val color: java.awt.Color,
    val size: Int,
  )

  private fun showConfettiAnimation() {
    // Find the root pane to use its glass pane for the overlay
    val rootPane = SwingUtilities.getRootPane(mainPanel) ?: return
    val glassPane = rootPane.glassPane as? JComponent ?: return

    // Get the panel location relative to glass pane
    val panelLocation = SwingUtilities.convertPoint(mainPanel, 0, 0, glassPane)
    val panelWidth = mainPanel.width.toFloat()
    val panelHeight = mainPanel.height

    // Create confetti overlay
    val confettiPanel = object : JBPanel<JBPanel<*>>() {
      private val confetti = mutableListOf<ConfettiPiece>()
      private val random = kotlin.random.Random.Default
      private var frameCount = 0
      private val maxFrames = 150 // 3 seconds at 50fps
      private var animTimer: javax.swing.Timer? = null

      init {
        isOpaque = false
        layout = null

        // Create confetti pieces
        // Colors: Gold, Red, Green, Blue, Purple
        val colors = listOf(
          java.awt.Color(0xFF, 0xD7, 0x00),
          java.awt.Color(0xF2, 0x54, 0x5B),
          java.awt.Color(0x4C, 0xAF, 0x50),
          java.awt.Color(0x21, 0x96, 0xF3),
          java.awt.Color(0x9C, 0x27, 0xB0),
        )

        repeat(50) {
          confetti.add(
            ConfettiPiece(
              x = random.nextFloat() * panelWidth + panelLocation.x,
              y = -random.nextFloat() * 200 + panelLocation.y,
              vx = (random.nextFloat() - 0.5f) * 4,
              vy = random.nextFloat() * 3 + 2,
              rotation = random.nextFloat() * 360,
              rotationSpeed = (random.nextFloat() - 0.5f) * 10,
              color = colors.random(),
              size = random.nextInt(6, 12),
            ),
          )
        }

        animTimer = javax.swing.Timer(20) {
          frameCount++
          confetti.forEach { c ->
            c.x += c.vx
            c.y += c.vy
            c.vy += 0.2f // gravity
            c.rotation += c.rotationSpeed
          }
          repaint()

          if (frameCount >= maxFrames) {
            animTimer?.stop()
            glassPane.remove(this)
            glassPane.isVisible = false
            glassPane.revalidate()
            glassPane.repaint()
          }
        }
        animTimer?.start()
      }

      override fun paintComponent(g: java.awt.Graphics) {
        super.paintComponent(g)
        val g2 = g as java.awt.Graphics2D
        g2.setRenderingHint(
          java.awt.RenderingHints.KEY_ANTIALIASING,
          java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
        )

        confetti.forEach { c ->
          // Only draw if within the main panel bounds
          if (c.y < panelLocation.y + panelHeight + 100) {
            g2.color = c.color
            val transform = g2.transform
            g2.translate(c.x.toDouble(), c.y.toDouble())
            g2.rotate(Math.toRadians(c.rotation.toDouble()))
            g2.fillRect(-c.size / 2, -c.size / 2, c.size, c.size)
            g2.transform = transform
          }
        }
      }
    }

    // Configure glass pane
    glassPane.layout = null
    confettiPanel.setBounds(0, 0, glassPane.width, glassPane.height)
    glassPane.add(confettiPanel)
    glassPane.isVisible = true
    glassPane.revalidate()
    glassPane.repaint()
  }

  private fun checkAndNotifyMilestone(
    title: String,
    current: Double,
    target: Double,
    isCurrency: Boolean,
  ) {
    val settings = RevenueCatSettingsState.getInstance()

    // Only notify if notifications are enabled
    if (!settings.milestoneNotificationsEnabled) {
      return
    }

    // Create a unique key for this milestone
    val milestoneKey = "$title:$target"

    // Only notify once per milestone
    if (notifiedMilestones.contains(milestoneKey)) {
      return
    }

    // Mark as notified
    notifiedMilestones.add(milestoneKey)

    // Create notification
    val format = "$%.2f"
    val currentStr = if (isCurrency) String.format(format, current) else current.toInt().toString()
    val targetStr = if (isCurrency) String.format(format, target) else target.toInt().toString()

    val message = "Congratulations! You've reached your $title milestone of " +
      "$targetStr (current: $currentStr)"
    com.intellij.notification.NotificationGroupManager.getInstance()
      .getNotificationGroup("RevenueCat Notifications")
      .createNotification(
        "Milestone Achieved!",
        message,
        com.intellij.notification.NotificationType.INFORMATION,
      )
      .notify(project)
  }

  private fun showDashboard(
    summary: RevenueSummary,
    offeringsResult: Result<List<com.revenuecat.plugin.api.models.Offering>>,
    subscriberOfferingsResult: Result<List<com.revenuecat.plugin.api.models.SubscriberOffering>>,
    projectName: String,
  ) {
    // Project Header Section
    contentPanel.add(createProjectHeader(projectName))
    contentPanel.add(Box.createVerticalStrut(20))

    // Overview Metrics Section
    contentPanel.add(createSectionTitle("Overview Metrics"))
    contentPanel.add(createSummaryPanel(summary))
    contentPanel.add(Box.createVerticalStrut(20))

    // Milestones Section
    val settings = RevenueCatSettingsState.getInstance()
    val hasAnyMilestone = (settings.mrrMilestoneEnabled && settings.mrrMilestone > 0.0) ||
      (settings.activeSubscriptionsMilestoneEnabled && settings.activeSubscriptionsMilestone > 0) ||
      (settings.activeTrialsMilestoneEnabled && settings.activeTrialsMilestone > 0) ||
      (settings.activeUsersMilestoneEnabled && settings.activeUsersMilestone > 0) ||
      (settings.newCustomersMilestoneEnabled && settings.newCustomersMilestone > 0) ||
      (settings.revenueMilestoneEnabled && settings.revenueMilestone > 0.0)

    if (hasAnyMilestone) {
      contentPanel.add(createSectionTitle("Milestones"))
      contentPanel.add(createMilestonesPanel(summary))
      contentPanel.add(Box.createVerticalStrut(20))
    }

    // Offerings Section
    contentPanel.add(createSectionTitle("Configured Offerings"))
    if (offeringsResult.isSuccess) {
      val subscriberOfferings = subscriberOfferingsResult.getOrNull() ?: emptyList()
      contentPanel.add(
        createOfferingsPanel(
          offeringsResult.getOrNull()!!,
          subscriberOfferings,
          subscriberOfferingsResult,
        ),
      )
    } else {
      contentPanel.add(createOfferingsPlaceholder(offeringsResult.exceptionOrNull()?.message))
    }
    contentPanel.add(Box.createVerticalStrut(20))

    // Codelabs Link Section
    contentPanel.add(createCodelabsLink())
    contentPanel.add(Box.createVerticalStrut(20))

    // Character Images Row
    contentPanel.add(createCharacterImagesRow())
    contentPanel.add(Box.createVerticalStrut(10))
  }

  private fun createCharacterImagesRow(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
    panel.border = JBUI.Borders.empty(10)
    panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 150)

    val characterImages = listOf(
      "/images/friend_shippy.png",
      "/images/friend_cartwheel.png",
      "/images/friend_coda.png",
      "/images/friend_gavi.png",
      "/images/friend_tama.png",
    )

    characterImages.forEachIndexed { index, imagePath ->
      try {
        val imageUrl = javaClass.getResource(imagePath)
        if (imageUrl != null) {
          val bufferedImage = ImageIO.read(imageUrl)

          // Scale images with smaller width while maintaining aspect ratio (100px width)
          val targetWidth = 100
          val originalWidth = bufferedImage.width
          val originalHeight = bufferedImage.height
          val aspectRatio = originalHeight.toDouble() / originalWidth.toDouble()
          val targetHeight = (targetWidth * aspectRatio).toInt()

          val scaledImage =
            bufferedImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
          val icon = ImageIcon(scaledImage)
          val imageLabel = JLabel(icon)

          panel.add(imageLabel)

          // Add spacing between images except after the last one
          if (index < characterImages.size - 1) {
            panel.add(Box.createHorizontalStrut(10))
          }
        }
      } catch (e: Exception) {
        println("RevenueCat Tool Window: Failed to load character image $imagePath: ${e.message}")
      }
    }

    panel.add(Box.createHorizontalGlue())

    return panel
  }

  private fun createCodelabsLink(): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(10)
    panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 60)

    // Codelabs link
    val codelabsLabel =
      JBLabel("<html>Learn more about RevenueCat? See <a href=''>Codelabs</a>.</html>")
    codelabsLabel.foreground = JBColor.GRAY
    codelabsLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    codelabsLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    codelabsLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        BrowserUtil.browse("https://revenuecat.github.io/")
      }
    })
    panel.add(codelabsLabel)
    panel.add(Box.createVerticalStrut(5))

    // Tour link
    val tourLabel = JBLabel("<html><a href=''>Take a tour of this plugin</a></html>")
    tourLabel.foreground = JBColor.GRAY
    tourLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    tourLabel.font = tourLabel.font.deriveFont(11f)
    tourLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    tourLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        startOnboarding()
      }

      override fun mouseEntered(e: MouseEvent?) {
        tourLabel.foreground = JBColor(0x589DF6, 0x589DF6)
      }

      override fun mouseExited(e: MouseEvent?) {
        tourLabel.foreground = JBColor.GRAY
      }
    })
    panel.add(tourLabel)

    return panel
  }

  private fun createSectionTitle(title: String): JComponent {
    val label = JBLabel(title)
    label.font = label.font.deriveFont(16f).deriveFont(java.awt.Font.BOLD)
    label.border = JBUI.Borders.empty(0, 0, 10, 0)
    label.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    return label
  }

  private fun createSummaryPanel(summary: RevenueSummary): JPanel {
    val panel = JBPanel<JBPanel<*>>(GridLayout(3, 2, 10, 10))
    panel.border = JBUI.Borders.empty(10)
    panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 350)

    panel.add(
      createMetricCard(
        "MRR",
        String.format("$%.2f", summary.mrr),
        "Monthly Recurring Revenue",
      ),
    )
    panel.add(
      createMetricCard(
        "Total Revenue",
        String.format("$%.2f", summary.revenue),
        "Last 28 days",
      ),
    )
    panel.add(createMetricCard("Active Subscriptions", summary.activeSubscriptions.toString()))
    panel.add(createMetricCard("Active Trials", summary.activeTrials.toString()))
    panel.add(createMetricCard("Active Users", summary.activeUsers.toString(), "Last 28 days"))
    panel.add(createMetricCard("New Customers", summary.newCustomers.toString(), "Last 28 days"))

    return panel
  }

  private fun createMetricCard(label: String, value: String, description: String? = null): JPanel {
    val card = JBPanel<JBPanel<*>>()
    card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
    card.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(JBColor.border(), 1),
      JBUI.Borders.empty(10),
    )

    val labelComponent = JBLabel(label)
    labelComponent.foreground = JBColor.GRAY

    val valueComponent = JBLabel(value)
    valueComponent.font = valueComponent.font.deriveFont(20f).deriveFont(java.awt.Font.BOLD)

    card.add(labelComponent)
    if (description != null) {
      val descComponent = JBLabel("<html><small>$description</small></html>")
      descComponent.foreground = JBColor.GRAY
      card.add(descComponent)
    }
    card.add(Box.createVerticalStrut(5))
    card.add(valueComponent)

    return card
  }

  private fun createSubscriptionsPanel(subscriptions: List<Subscription>?): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(10)

    val safeSubscriptions = subscriptions ?: emptyList()

    if (safeSubscriptions.isEmpty()) {
      panel.add(JBLabel("No subscriptions found"))
    } else {
      safeSubscriptions.forEach { subscription ->
        panel.add(createSubscriptionCard(subscription))
        panel.add(Box.createVerticalStrut(10))
      }
    }

    return panel
  }

  private fun createSubscriptionCard(subscription: Subscription): JPanel {
    val card = JBPanel<JBPanel<*>>()
    card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
    card.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(JBColor.border(), 1),
      JBUI.Borders.empty(10),
    )

    card.add(JBLabel("Product: ${subscription.productId}"))
    card.add(JBLabel("Status: ${subscription.status}"))
    subscription.price?.let {
      card.add(JBLabel("Price: $it ${subscription.currency ?: ""}"))
    }
    subscription.expiresAt?.let {
      card.add(JBLabel("Expires: $it"))
    }

    return card
  }

  private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    return format.format(date)
  }

  private fun createOfferingsPanel(
    offerings: List<com.revenuecat.plugin.api.models.Offering>,
    subscriberOfferings: List<com.revenuecat.plugin.api.models.SubscriberOffering>,
    subscriberOfferingsResult: Result<List<com.revenuecat.plugin.api.models.SubscriberOffering>>,
  ): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(10, 10, 0, 10)
    panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

    if (offerings.isEmpty()) {
      val label = JBLabel("No offerings found")
      label.alignmentX = java.awt.Component.LEFT_ALIGNMENT
      panel.add(label)
    } else {
      offerings.forEachIndexed { index, offering ->
        // Find matching subscriber offering with paywall data - try both lookupKey and id
        val subscriberOffering = subscriberOfferings.find {
          it.identifier == offering.lookupKey || it.identifier == offering.id
        }

        val card = createOfferingCard(offering, subscriberOffering)
        card.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        panel.add(card)
        // Only add spacing between cards, not after the last one
        if (index < offerings.size - 1) {
          panel.add(Box.createVerticalStrut(5))
        }
      }
    }

    return panel
  }

  private fun createOfferingCard(
    offering: com.revenuecat.plugin.api.models.Offering,
    subscriberOffering: com.revenuecat.plugin.api.models.SubscriberOffering?,
  ): JPanel {
    val card = JBPanel<JBPanel<*>>()
    card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
    card.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(JBColor.border(), 1),
      JBUI.Borders.empty(10),
    )

    // Offering header
    val headerPanel = JBPanel<JBPanel<*>>()
    headerPanel.layout = BoxLayout(headerPanel, BoxLayout.X_AXIS)
    headerPanel.alignmentX = java.awt.Component.LEFT_ALIGNMENT

    val nameLabel = JBLabel("<html><b>${offering.displayName}</b></html>")
    headerPanel.add(nameLabel)
    headerPanel.add(Box.createHorizontalStrut(10))

    if (offering.isCurrent) {
      val currentBadge = JBLabel("<html><small>[Current]</small></html>")
      currentBadge.foreground = JBColor(0x4CAF50, 0x81C784)
      headerPanel.add(currentBadge)
    }

    headerPanel.add(Box.createHorizontalGlue())

    // Check if paywall exists either in v1 API data OR in our cache
    val settings = RevenueCatSettingsState.getInstance()
    val hasCachedPaywall = settings.getCachedPaywallId(offering.id) != null
    val hasPaywallInApi = subscriberOffering?.hasPaywall() == true
    val hasPaywall = hasPaywallInApi || hasCachedPaywall

    // Add "View Paywall" button if paywall exists, or "Add Paywall" button if it doesn't
    if (hasPaywall) {
      val paywallButton = JButton("Paywall")
      paywallButton.addActionListener {
        // Navigate directly to offering page with paywall tab
        val settings = RevenueCatSettingsState.getInstance()
        val projectId = settings.projectId
        val projectIdForUrl = if (projectId.startsWith("proj")) {
          projectId.removePrefix("proj")
        } else {
          projectId
        }

        // Add 'ofr' prefix to offering ID if not present
        val offeringIdForUrl = if (!offering.id.startsWith("ofr")) {
          "ofr${offering.id}"
        } else {
          offering.id
        }

        val baseUrl = "https://app.revenuecat.com/projects/$projectIdForUrl"
        val paywallUrl =
          "$baseUrl/product-catalog/offerings/$offeringIdForUrl?tab=paywall&$UTM_PARAMS"
        BrowserUtil.browse(paywallUrl)
      }
      headerPanel.add(paywallButton)
      headerPanel.add(Box.createHorizontalStrut(5))
    } else {
      // Offering doesn't have a paywall yet - show "Add Paywall" button
      val addPaywallButton = JButton("Add Paywall")
      addPaywallButton.addActionListener {
        createAndShowPaywall(offering, headerPanel, subscriberOffering)
      }
      headerPanel.add(addPaywallButton)
      headerPanel.add(Box.createHorizontalStrut(5))
    }

    // Add "Manage" button to navigate to offering page
    val manageButton = JButton("Manage")
    manageButton.addActionListener {
      val settings = RevenueCatSettingsState.getInstance()
      val projectId = settings.projectId
      val projectIdForUrl = if (projectId.startsWith("proj")) {
        projectId.removePrefix("proj")
      } else {
        projectId
      }

      // Add 'ofr' prefix to offering ID if not present
      val offeringIdForUrl = if (!offering.id.startsWith("ofr")) {
        "ofr${offering.id}"
      } else {
        offering.id
      }

      val baseUrl = "https://app.revenuecat.com/projects/$projectIdForUrl"
      val offeringUrl =
        "$baseUrl/product-catalog/offerings/$offeringIdForUrl?$UTM_PARAMS"
      BrowserUtil.browse(offeringUrl)
    }
    headerPanel.add(manageButton)

    card.add(headerPanel)
    card.add(Box.createVerticalStrut(5))

    // Offering details
    val packagesCount = offering.packages?.getPackagesSafe()?.size ?: 0
    val packagesLabel = JBLabel("<html><small>Packages: $packagesCount</small></html>")
    packagesLabel.foreground = JBColor.GRAY
    packagesLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    card.add(packagesLabel)

    // Show packages if available
    offering.packages?.getPackagesSafe()?.let { packages ->
      if (packages.isNotEmpty()) {
        card.add(Box.createVerticalStrut(8))
        val packagesTitle = JBLabel("<html><b><small>Packages:</small></b></html>")
        packagesTitle.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        card.add(packagesTitle)
        card.add(Box.createVerticalStrut(5))

        packages.forEach { pkg ->
          val pkgLabel =
            JBLabel("<html><small>• ${pkg.displayName} (${pkg.lookupKey})</small></html>")
          pkgLabel.foreground = JBColor.GRAY
          pkgLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
          pkgLabel.border = JBUI.Borders.emptyLeft(10)
          card.add(pkgLabel)
        }
      }
    }

    // Set maximum size to allow proper width but use preferred height
    card.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

    return card
  }

  private fun createOfferingsPlaceholder(errorMessage: String?): JPanel {
    val panel = JBPanel<JBPanel<*>>()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.border = JBUI.Borders.empty(10)
    panel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.maximumSize = java.awt.Dimension(Integer.MAX_VALUE, 100)

    val messageLabel = JBLabel("<html>Offerings unavailable.<br/><br/>$errorMessage</html>")
    messageLabel.foreground = JBColor.GRAY
    messageLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
    panel.add(messageLabel)

    return panel
  }

  /**
   * Create a new paywall for an offering and show the preview
   */
  private fun createAndShowPaywall(
    offering: com.revenuecat.plugin.api.models.Offering,
    headerPanel: JPanel,
    subscriberOffering: com.revenuecat.plugin.api.models.SubscriberOffering?,
  ) {
    val settings = RevenueCatSettingsState.getInstance()
    val apiService = com.revenuecat.plugin.services.RevenueCatApiService.getInstance()

    // Show progress indicator
    com.intellij.openapi.progress.ProgressManager.getInstance().run(
      object :
        com.intellij.openapi.progress.Task.Backgroundable(null, "Creating Paywall...", false) {
        override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
          indicator.text = "Creating paywall for ${offering.displayName}..."

          // Call create paywall API
          val createResult = apiService.createPaywall(
            offeringId = offering.id,
            name = "Paywall for ${offering.displayName}",
          )

          com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            if (createResult.isSuccess) {
              val paywallResponse = createResult.getOrNull()!!
              val paywallId = paywallResponse.id

              // Cache the paywall ID
              settings.cachePaywallId(offering.id, paywallId)

              // Show success message
              com.intellij.openapi.ui.Messages.showInfoMessage(
                "Paywall created successfully!\n\nPaywall ID: $paywallId",
                "Paywall Created",
              )

              // Refresh the UI to update the button
              loadData()

              // Show the paywall preview
              val projectId = settings.projectId.removePrefix("proj")
              val baseUrl = "https://app.revenuecat.com/projects/$projectId"
              val previewUrl = "$baseUrl/paywalls/$paywallId/builder?$UTM_PARAMS"
              showWebPreview(offering.displayName, previewUrl)
            } else {
              val error = createResult.exceptionOrNull()
              val errorMessage = error?.message ?: ""

              // If paywall already exists, just redirect to the paywall page
              if (errorMessage.contains("already a paywall", ignoreCase = true)) {
                // Redirect to offering page with paywall tab
                val projectId = settings.projectId.removePrefix("proj")
                val offeringIdForUrl = if (!offering.id.startsWith("ofr")) {
                  "ofr${offering.id}"
                } else {
                  offering.id
                }
                val baseUrl = "https://app.revenuecat.com/projects/$projectId"
                val paywallUrl =
                  "$baseUrl/product-catalog/offerings/$offeringIdForUrl" +
                    "?tab=paywall&$UTM_PARAMS"
                BrowserUtil.browse(paywallUrl)
              } else {
                // Show error dialog for other errors
                com.intellij.openapi.ui.Messages.showErrorDialog(
                  "Failed to create paywall:\n\n$errorMessage",
                  "Create Paywall Failed",
                )
              }
            }
          }
        }
      },
    )
  }

  private fun showPaywallPreview(
    offeringName: String,
    subscriberOffering: com.revenuecat.plugin.api.models.SubscriberOffering?,
  ) {
    val settings = RevenueCatSettingsState.getInstance()
    val projectId = settings.projectId.removePrefix("proj")
    val apiService = com.revenuecat.plugin.services.RevenueCatApiService.getInstance()

    // Step 1: Get offering ID from v2 API
    val offeringsResult = apiService.fetchOfferings()
    if (offeringsResult.isSuccess) {
      val offerings = offeringsResult.getOrNull() ?: emptyList()
      val matchingOffering = offerings.find { it.lookupKey == offeringName }

      if (matchingOffering != null) {
        val baseUrl = "https://app.revenuecat.com/projects/$projectId"

        // Step 2: Check cache first
        val cachedPaywallId = settings.getCachedPaywallId(matchingOffering.id)
        if (cachedPaywallId != null) {
          // Use cached paywall ID
          val previewUrl = "$baseUrl/paywalls/$cachedPaywallId/builder&$UTM_PARAMS"
          showWebPreview(offeringName, previewUrl)
          return
        }

        // Step 3: Try to get paywall ID from internal API using OAuth
        if (settings.hasValidOAuthToken() || settings.canRefreshOAuthToken()) {
          try {
            val internalClient =
              com.revenuecat.plugin.api.RevenueCatInternalApiClient()
            val paywallsResult = internalClient.listPaywalls(settings.projectId)

            if (paywallsResult.isSuccess) {
              val paywalls = paywallsResult.getOrNull() ?: emptyList()

              // Find paywall by offering ID
              val paywall = paywalls.find { it.offeringId == matchingOffering.id }

              if (paywall != null) {
                // Cache it for next time
                val paywallId = paywall.paywallId
                settings.cachePaywallId(matchingOffering.id, paywallId)

                // Success! Build exact paywall builder URL
                val previewUrl = "$baseUrl/paywalls/$paywallId/builder?$UTM_PARAMS"
                showWebPreview(offeringName, previewUrl)
                return
              }
            }
          } catch (e: Exception) {
            // If any error occurs, fall back to offering URL
            e.printStackTrace()
          }
        }
      }
    }

    // Fallback: link to offering page
    showFallbackOfferingUrl(offeringName, projectId)
  }

  private fun showFallbackOfferingUrl(offeringName: String, projectId: String) {
    // Link to offering page instead of paywall builder
    val baseUrl = "https://app.revenuecat.com/projects/$projectId/offerings"
    val previewUrl = "$baseUrl/$offeringName?$UTM_PARAMS"
    showWebPreview(offeringName, previewUrl)
  }

  private fun showWebPreview(offeringName: String, url: String) {
    // Open URL in default browser
    com.intellij.ide.BrowserUtil.browse(url)
  }

  /**
   * Action classes for toolbar buttons
   */
  private inner class RefreshAction : AnAction(
    "Refresh",
    "Refresh RevenueCat data",
    AllIcons.Actions.Refresh,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      loadData()
    }
  }

  private inner class SettingsAction : AnAction(
    "Settings",
    "Open RevenueCat settings",
    AllIcons.General.Settings,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance().showSettingsDialog(
        project,
        RevenueCatSettingsConfigurable::class.java,
      )
    }
  }

  private inner class ShareAction : AnAction(
    "Share Metrics",
    "Manage project sharing settings",
    try {
      // Try to use CodeWithMe.CwmVerified icon if available
      AllIcons.CodeWithMe.CwmShared
    } catch (e: Exception) {
      com.intellij.icons.AllIcons.General.User
    },
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      val settings = RevenueCatSettingsState.getInstance()
      val projectId = settings.projectId

      // Remove 'proj' prefix for URL if present
      val projectIdForUrl = if (projectId.startsWith("proj")) {
        projectId.removePrefix("proj")
      } else {
        projectId
      }

      val baseUrl = "https://app.revenuecat.com/projects/$projectIdForUrl/settings"
      val shareUrl = "$baseUrl/share?$UTM_PARAMS"
      BrowserUtil.browse(shareUrl)
    }

    override fun update(e: AnActionEvent) {
      // Only enable if project is configured
      val settings = RevenueCatSettingsState.getInstance()
      e.presentation.isEnabled = settings.isConfigured() && settings.projectId.isNotBlank()
    }
  }

  private inner class SdkSetupWizardAction : AnAction(
    "SDK Setup Wizard",
    "Open SDK integration wizard",
    AllIcons.Actions.Lightning,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      val wizard = com.revenuecat.plugin.wizard.SdkIntegrationWizard(project)
      wizard.show()
    }
  }

  private inner class MilestoneAction : AnAction(
    "Milestones",
    "Configure revenue and user milestones",
    AllIcons.Debugger.Db_array,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      val dialog = com.revenuecat.plugin.milestone.MilestoneConfigurationDialog(project)
      if (dialog.showAndGet()) {
        // Refresh UI to show updated progress
        loadData()
      }
    }
  }

  private inner class ReleaseNotesAction : AnAction(
    "Release Notes",
    "View latest SDK release notes",
    AllIcons.Gutter.SuggestedRefactoringBulb,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      ReleaseNotesDialog(project).show()
    }
  }

  private inner class BlogAction : AnAction(
    "Blog Posts",
    "View latest RevenueCat blog articles",
    AllIcons.Actions.InlayRenameInNoCodeFilesActive,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      BlogArticlesDialog(project).show()
    }
  }

  private inner class AIAssistantAction :
    AnAction(
      "AI Assistant",
      "Toggle AI Assistant panel",
      AllIcons.Diff.MagicResolve,
    ),
    Toggleable {
    private var isSelected = false

    override fun actionPerformed(e: AnActionEvent) {
      toggleAIPanel()
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      Toggleable.setSelected(e.presentation, isSelected)
    }

    fun updateToggleState(selected: Boolean) {
      isSelected = selected
    }
  }

  fun getContent(): JComponent = mainPanel
}

/**
 * Custom progress bar that maintains RevenueCat primary color (#F2545B) across theme changes
 */
private class RevenueCatProgressBar : JProgressBar(0, 100) {

  companion object {
    val REVENUECAT_COLOR = java.awt.Color(0xF2, 0x54, 0x5B)

    // Light gray track color for both themes
    val TRACK_COLOR_LIGHT = java.awt.Color(0xE0, 0xE0, 0xE0)
    val TRACK_COLOR_DARK = java.awt.Color(0x4A, 0x4A, 0x4A)
  }

  init {
    foreground = REVENUECAT_COLOR
    applyCustomUI()
  }

  override fun updateUI() {
    super.updateUI()
    // Reapply custom UI after theme changes
    foreground = REVENUECAT_COLOR
    applyCustomUI()
  }

  private fun applyCustomUI() {
    setUI(object : javax.swing.plaf.basic.BasicProgressBarUI() {
      // Text color on the filled (progress) part - always white
      override fun getSelectionForeground(): java.awt.Color = java.awt.Color.WHITE

      // Text color on the unfilled (background) part - white on dark mode, dark on light mode
      override fun getSelectionBackground(): java.awt.Color {
        return if (JBColor.isBright()) java.awt.Color.DARK_GRAY else java.awt.Color.WHITE
      }

      override fun paintDeterminate(g: java.awt.Graphics, c: javax.swing.JComponent) {
        val g2 = g as java.awt.Graphics2D
        g2.setRenderingHint(
          java.awt.RenderingHints.KEY_ANTIALIASING,
          java.awt.RenderingHints.VALUE_ANTIALIAS_ON,
        )

        val width = progressBar.width
        val height = progressBar.height
        val arcSize = 10

        // Draw light gray track (background) with rounded corners
        val trackColor = if (JBColor.isBright()) TRACK_COLOR_LIGHT else TRACK_COLOR_DARK
        g2.color = trackColor
        g2.fillRoundRect(0, 0, width, height, arcSize, arcSize)

        // Draw progress with rounded corners - always use RevenueCat color
        val amountFull = getAmountFull(progressBar.insets, width, height)
        g2.color = REVENUECAT_COLOR
        if (amountFull > 0) {
          g2.fillRoundRect(0, 0, amountFull, height, arcSize, arcSize)
        }

        // Paint the string
        if (progressBar.isStringPainted) {
          paintString(g, 0, 0, width, height, amountFull, progressBar.insets)
        }
      }
    })
  }
}
