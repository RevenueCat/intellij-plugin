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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.Action
import javax.swing.JComponent

/**
 * Dialog for the RevenueCat AI Assistant
 */
class AIAssistantDialog(project: Project?) : DialogWrapper(project) {

  private val chatPanel = AIChatPanel()

  init {
    title = "RevenueCat AI Assistant"
    setSize(700, 600)
    init()
  }

  override fun createCenterPanel(): JComponent {
    return chatPanel
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction.apply { putValue(Action.NAME, "Close") })
  }
}
