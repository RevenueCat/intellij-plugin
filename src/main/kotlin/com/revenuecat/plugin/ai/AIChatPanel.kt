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
package com.revenuecat.plugin.ai

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

/**
 * Chat panel for interacting with the RevenueCat AI Agent
 * Uses modern bubble-style chat UI similar to Google's AI Studio
 */
class AIChatPanel : JBPanel<JBPanel<*>>() {

  private val chatHistoryPanel = JBPanel<JBPanel<*>>()
  private val inputField = JBTextField()
  private val sendButton = JButton()
  private val clearButton = JButton()
  private val scope = CoroutineScope(Dispatchers.Default)
  private var scrollPane: JBScrollPane? = null

  // Markdown parser for rendering AI responses
  private val markdownFlavour = CommonMarkFlavourDescriptor()
  private val markdownParser = MarkdownParser(markdownFlavour)

  private data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val isLoading: Boolean = false,
  )

  private val chatHistory = mutableListOf<ChatMessage>()

  init {
    layout = BorderLayout()
    border = JBUI.Borders.empty(8)
    background = JBColor(0xFAFAFA, 0x2B2B2B)

    setupUI()
  }

  private fun setupUI() {
    // Header
    val headerPanel = createHeaderPanel()
    add(headerPanel, BorderLayout.NORTH)

    // Chat history area with custom background
    chatHistoryPanel.layout = BoxLayout(chatHistoryPanel, BoxLayout.Y_AXIS)
    chatHistoryPanel.border = JBUI.Borders.empty(12)
    chatHistoryPanel.background = JBColor(0xFAFAFA, 0x2B2B2B)

    scrollPane = JBScrollPane(chatHistoryPanel)
    scrollPane?.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    scrollPane?.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    scrollPane?.border = BorderFactory.createEmptyBorder()
    scrollPane?.background = JBColor(0xFAFAFA, 0x2B2B2B)
    scrollPane?.viewport?.background = JBColor(0xFAFAFA, 0x2B2B2B)

    add(scrollPane, BorderLayout.CENTER)

    // Input area
    val inputPanel = createInputPanel()
    add(inputPanel, BorderLayout.SOUTH)

    // Show welcome message
    showWelcomeMessage()
  }

  private fun createHeaderPanel(): JPanel {
    val headerPanel = JBPanel<JBPanel<*>>(BorderLayout())
    headerPanel.border = JBUI.Borders.empty(0, 4, 12, 4)
    headerPanel.isOpaque = false

    val titlePanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 0))
    titlePanel.isOpaque = false

    val iconLabel = JBLabel(AllIcons.Diff.MagicResolve)
    titlePanel.add(iconLabel)

    val titleLabel = JBLabel("RevenueCat AI")
    titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 15f)
    titlePanel.add(titleLabel)

    headerPanel.add(titlePanel, BorderLayout.WEST)

    val statusLabel = JBLabel()
    updateStatusLabel(statusLabel)
    headerPanel.add(statusLabel, BorderLayout.EAST)

    return headerPanel
  }

  private fun createInputPanel(): JPanel {
    val inputWrapper = JBPanel<JBPanel<*>>(BorderLayout())
    inputWrapper.border = JBUI.Borders.empty(12, 0, 0, 0)
    inputWrapper.isOpaque = false

    // Input container with rounded border
    val inputContainer = RoundedPanel(12, JBColor(0xFFFFFF, 0x3C3C3C))
    inputContainer.layout = BorderLayout()
    inputContainer.border = BorderFactory.createCompoundBorder(
      RoundedBorder(12, JBColor(0xE0E0E0, 0x4A4A4A)),
      JBUI.Borders.empty(4, 12),
    )

    inputField.border = BorderFactory.createEmptyBorder()
    inputField.background = JBColor(0xFFFFFF, 0x3C3C3C)
    inputField.font = inputField.font.deriveFont(13f)
    inputField.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
          e.consume()
          sendMessage()
        }
      }
    })

    // Send button with text
    sendButton.text = "Send"
    sendButton.addActionListener { sendMessage() }

    // Clear button with text
    clearButton.text = "Clear"
    clearButton.addActionListener { clearChat() }

    val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 4, 0))
    buttonPanel.isOpaque = false
    buttonPanel.add(clearButton)
    buttonPanel.add(sendButton)

    inputContainer.add(inputField, BorderLayout.CENTER)
    inputContainer.add(buttonPanel, BorderLayout.EAST)

    inputWrapper.add(inputContainer, BorderLayout.CENTER)

    return inputWrapper
  }

  private fun updateStatusLabel(label: JBLabel) {
    val settings = RevenueCatSettingsState.getInstance()
    if (settings.isAIConfigured()) {
      val model = AIModelOption.entries.find { it.name == settings.aiModel }
      label.text = model?.displayName ?: "Unknown"
      label.foreground = JBColor(0x666666, 0x999999)
      label.font = label.font.deriveFont(11f)
    } else {
      label.text = "Not configured"
      label.foreground = JBColor(0xE65100, 0xFFB74D)
      label.font = label.font.deriveFont(Font.ITALIC, 11f)
    }
  }

  private fun showWelcomeMessage() {
    val settings = RevenueCatSettingsState.getInstance()
    if (!settings.isAIConfigured()) {
      addMessage(
        """
        **Welcome to RevenueCat AI Assistant!**

        To get started, please configure your AI settings:
        1. Go to **Settings** → **RevenueCat** → **AI Settings**
        2. Enable AI Assistant
        3. Enter your API key (OpenAI, Anthropic, or Google)
        4. Select your preferred model

        Once configured, you can ask questions like:
        - "Show me my current metrics"
        - "What offerings do I have configured?"
        - "How do I set up paywalls?"
        """.trimIndent(),
        isUser = false,
      )
    } else {
      addMessage(
        """
        **Hello! I'm your RevenueCat AI Assistant.**

        I can help you with:
        - 📊 Viewing subscription metrics (MRR, trials, revenue)
        - 📦 Exploring your offerings and packages
        - 🔧 Checking your project configuration
        - 📚 Finding documentation and resources

        What would you like to know?
        """.trimIndent(),
        isUser = false,
      )
    }
  }

  private fun sendMessage() {
    val message = inputField.text.trim()
    if (message.isEmpty()) return

    inputField.text = ""
    addMessage(message, isUser = true)

    val settings = RevenueCatSettingsState.getInstance()
    if (!settings.isAIConfigured()) {
      addMessage(
        "AI Assistant is not configured. Please set up your API key in " +
          "**Settings → RevenueCat → AI Settings**.",
        isUser = false,
        isError = true,
      )
      return
    }

    // Show loading indicator
    val loadingPanel = addMessage("Thinking...", isUser = false, isLoading = true)

    // Run query in background
    scope.launch {
      val agentService = RevenueCatAIAgentService.getInstance()
      val result = agentService.runQuery(message)

      SwingUtilities.invokeLater {
        // Remove loading message
        removeMessage(loadingPanel)

        result.fold(
          onSuccess = { response ->
            addMessage(response, isUser = false)
          },
          onFailure = { error ->
            addMessage("Error: ${error.message}", isUser = false, isError = true)
          },
        )
      }
    }
  }

  private fun markdownToHtml(markdown: String): String {
    return try {
      val parsedTree = markdownParser.buildMarkdownTreeFromString(markdown)
      val html = HtmlGenerator(markdown, parsedTree, markdownFlavour).generateHtml()
      // Remove the outer <body> tags that the generator adds
      html.removePrefix("<body>").removeSuffix("</body>")
    } catch (e: Exception) {
      // Fallback to escaping HTML if parsing fails
      escapeHtml(markdown)
    }
  }

  private fun escapeHtml(text: String): String {
    return text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\n", "<br>")
  }

  private fun addMessage(
    content: String,
    isUser: Boolean,
    isError: Boolean = false,
    isLoading: Boolean = false,
  ): JPanel {
    val message = ChatMessage(content, isUser, isError, isLoading)
    chatHistory.add(message)

    val messageRow = createMessageRow(message)
    chatHistoryPanel.add(messageRow)
    chatHistoryPanel.add(Box.createVerticalStrut(6))

    chatHistoryPanel.revalidate()
    chatHistoryPanel.repaint()

    // Scroll to bottom
    SwingUtilities.invokeLater {
      scrollPane?.let { sp ->
        val vertical = sp.verticalScrollBar
        vertical.value = vertical.maximum
      }
    }

    return messageRow
  }

  private fun removeMessage(panel: JPanel) {
    val index = chatHistoryPanel.components.indexOf(panel)
    if (index >= 0) {
      chatHistoryPanel.remove(index)
      // Also remove the strut after it if it exists
      if (index < chatHistoryPanel.componentCount) {
        chatHistoryPanel.remove(index)
      }
      chatHistory.removeLastOrNull()
      chatHistoryPanel.revalidate()
      chatHistoryPanel.repaint()
    }
  }

  private fun createMessageRow(message: ChatMessage): JPanel {
    val rowPanel = JBPanel<JBPanel<*>>(BorderLayout())
    rowPanel.isOpaque = false
    rowPanel.border = JBUI.Borders.empty(
      0,
      if (message.isUser) 40 else 0,
      0,
      if (message.isUser) 0 else 40,
    )

    val bubblePanel = createBubblePanel(message)

    if (message.isUser) {
      // User messages on the right
      val rightAlign = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 0, 0))
      rightAlign.isOpaque = false
      rightAlign.add(bubblePanel)
      rowPanel.add(rightAlign, BorderLayout.CENTER)
    } else {
      // AI messages on the left with avatar
      val leftPanel = JBPanel<JBPanel<*>>(BorderLayout())
      leftPanel.isOpaque = false

      val avatarPanel = createAvatarPanel(message.isUser)
      leftPanel.add(avatarPanel, BorderLayout.WEST)

      val contentPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 0))
      contentPanel.isOpaque = false
      contentPanel.add(bubblePanel)
      leftPanel.add(contentPanel, BorderLayout.CENTER)

      rowPanel.add(leftPanel, BorderLayout.CENTER)
    }

    // Constrain the row height to its preferred size (prevents BoxLayout from stretching)
    rowPanel.maximumSize = Dimension(Int.MAX_VALUE, rowPanel.preferredSize.height)

    return rowPanel
  }

  private fun createAvatarPanel(isUser: Boolean): JPanel {
    val avatarPanel = JBPanel<JBPanel<*>>(BorderLayout())
    avatarPanel.isOpaque = false
    avatarPanel.preferredSize = Dimension(32, 32)
    avatarPanel.border = JBUI.Borders.emptyTop(2)

    val icon = if (isUser) AllIcons.General.User else AllIcons.Diff.MagicResolve
    val avatarLabel = JBLabel(icon)
    avatarLabel.horizontalAlignment = JBLabel.CENTER
    avatarPanel.add(avatarLabel, BorderLayout.NORTH)

    return avatarPanel
  }

  private fun createBubblePanel(message: ChatMessage): JPanel {
    val bgColor = when {
      message.isError -> JBColor(0xFFEBEE, 0x4A2020)
      message.isLoading -> JBColor(0xF5F5F5, 0x3C3C3C)
      message.isUser -> JBColor(0x1976D2, 0x1565C0)
      else -> JBColor(0xFFFFFF, 0x3C3C3C)
    }

    val textColor = when {
      message.isError -> JBColor(0xC62828, 0xEF9A9A)
      message.isUser -> JBColor(0xFFFFFF, 0xFFFFFF)
      else -> JBColor(0x212121, 0xE0E0E0)
    }

    val bubble = RoundedPanel(16, bgColor)
    bubble.layout = BorderLayout()
    bubble.border = JBUI.Borders.empty(10, 14)

    if (!message.isUser && !message.isLoading) {
      // Add subtle border for AI messages
      bubble.border = BorderFactory.createCompoundBorder(
        RoundedBorder(16, JBColor(0xE0E0E0, 0x4A4A4A)),
        JBUI.Borders.empty(10, 14),
      )
    }

    // Use JEditorPane for HTML rendering
    val textPane = JEditorPane()
    textPane.contentType = "text/html"
    textPane.isEditable = false
    textPane.isOpaque = false
    textPane.border = BorderFactory.createEmptyBorder()

    // Setup HTML styling - use only CSS properties supported by Swing's HTMLEditorKit
    val kit = HTMLEditorKit()
    val styleSheet = StyleSheet()
    val colorHex = String.format("#%06X", textColor.rgb and 0xFFFFFF)

    // Add rules one at a time with simple CSS (Swing doesn't support CSS3)
    styleSheet.addRule("body { font-size: 13pt; color: $colorHex; margin: 0; padding: 0; }")
    styleSheet.addRule("p { margin: 4px 0; }")
    styleSheet.addRule("strong { font-weight: bold; }")
    styleSheet.addRule("em { font-style: italic; }")
    styleSheet.addRule(
      "code { font-family: monospace; background-color: #E8E8E8; padding: 2px 4px; }",
    )
    styleSheet.addRule(
      "pre { background-color: #F5F5F5; padding: 8px; font-size: 11pt; font-family: monospace; }",
    )
    styleSheet.addRule("ul { margin: 4px 0; padding-left: 20px; }")
    styleSheet.addRule("ol { margin: 4px 0; padding-left: 20px; }")
    styleSheet.addRule("li { margin: 2px 0; }")
    styleSheet.addRule("h1 { font-size: 16pt; font-weight: bold; margin: 8px 0 4px 0; }")
    styleSheet.addRule("h2 { font-size: 15pt; font-weight: bold; margin: 6px 0 4px 0; }")
    styleSheet.addRule("h3 { font-size: 14pt; font-weight: bold; margin: 4px 0 2px 0; }")
    styleSheet.addRule("a { color: #1976D2; }")

    kit.styleSheet = styleSheet
    textPane.editorKit = kit

    val htmlContent = when {
      message.isLoading -> "<i>${message.content}</i>"
      message.isUser -> escapeHtml(message.content)
      else -> markdownToHtml(message.content)
    }
    textPane.text = "<html><body>$htmlContent</body></html>"

    bubble.add(textPane, BorderLayout.CENTER)

    return bubble
  }

  private fun clearChat() {
    chatHistory.clear()
    chatHistoryPanel.removeAll()
    chatHistoryPanel.revalidate()
    chatHistoryPanel.repaint()
    showWelcomeMessage()
  }

  /**
   * Refresh the panel when settings change
   */
  fun refresh() {
    ApplicationManager.getApplication().invokeLater {
      clearChat()
    }
  }

  /**
   * Custom panel with rounded corners
   */
  private class RoundedPanel(
    private val cornerRadius: Int,
    private val bgColor: Color,
  ) : JBPanel<JBPanel<*>>() {

    init {
      isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
      val g2 = g.create() as Graphics2D
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      g2.color = bgColor
      g2.fill(
        RoundRectangle2D.Float(
          0f,
          0f,
          width.toFloat(),
          height.toFloat(),
          cornerRadius.toFloat(),
          cornerRadius.toFloat(),
        ),
      )

      g2.dispose()
      super.paintComponent(g)
    }
  }

  /**
   * Custom border with rounded corners
   */
  private class RoundedBorder(
    private val cornerRadius: Int,
    private val borderColor: Color,
  ) : javax.swing.border.AbstractBorder() {

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      val g2 = g.create() as Graphics2D
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      g2.color = borderColor
      g2.draw(
        RoundRectangle2D.Float(
          x.toFloat() + 0.5f,
          y.toFloat() + 0.5f,
          width.toFloat() - 1f,
          height.toFloat() - 1f,
          cornerRadius.toFloat(),
          cornerRadius.toFloat(),
        ),
      )

      g2.dispose()
    }

    override fun getBorderInsets(c: Component) = JBUI.insets(1)

    override fun isBorderOpaque() = false
  }
}
