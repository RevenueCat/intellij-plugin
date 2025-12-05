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
package com.revenuecat.plugin.ui.paywall

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Web-based paywall preview using JCEF to embed RevenueCat dashboard
 */
class PaywallWebPreview(
  private val offeringName: String,
  private val previewUrl: String,
) : DialogWrapper(true) {

  private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) {
    JBCefBrowser()
  } else {
    null
  }

  init {
    title = "Paywall Preview - $offeringName"
    init()
    browser?.loadURL(previewUrl)
  }

  override fun createCenterPanel(): JComponent? {
    return browser?.component?.apply {
      preferredSize = Dimension(1200, 800)
    }
  }

  companion object {
    fun show(offeringName: String, previewUrl: String) {
      ApplicationManager.getApplication().invokeLater {
        if (!JBCefApp.isSupported()) {
          // JCEF not available, open in external browser
          val result = Messages.showYesNoDialog(
            "Browser preview not available in this IDE. Open in external browser?",
            "Paywall Preview - $offeringName",
            Messages.getQuestionIcon(),
          )
          if (result == Messages.YES) {
            BrowserUtil.browse(previewUrl)
          }
        } else {
          try {
            val dialog = PaywallWebPreview(offeringName, previewUrl)
            dialog.show()
          } catch (e: Exception) {
            // Fallback to external browser if JCEF fails
            e.printStackTrace()
            val result = Messages.showYesNoDialog(
              "Failed to open embedded browser. Open in external browser?\n\nError: ${e.message}",
              "Paywall Preview - $offeringName",
              Messages.getQuestionIcon(),
            )
            if (result == Messages.YES) {
              BrowserUtil.browse(previewUrl)
            }
          }
        }
      }
    }
  }
}
