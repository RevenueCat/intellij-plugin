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
package com.revenuecat.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.revenuecat.plugin.services.RevenueCatApiService

/**
 * Action to refresh RevenueCat data
 */
class RefreshDataAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val apiService = RevenueCatApiService.getInstance()

    // Clear cache to force refresh
    apiService.clearCache()

    // Find and refresh the tool window
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow("RevenueCat")

    toolWindow?.show()
  }

  override fun update(e: AnActionEvent) {
    // Enable the action only when a project is open
    e.presentation.isEnabledAndVisible = e.project != null
  }
}
