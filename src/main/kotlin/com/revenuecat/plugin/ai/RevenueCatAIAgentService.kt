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

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.llm.LLModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.revenuecat.plugin.settings.RevenueCatSettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Supported AI providers for the RevenueCat AI Agent
 */
enum class AIProvider(val displayName: String) {
  OPENAI("OpenAI"),
  ANTHROPIC("Anthropic (Claude)"),
  GOOGLE("Google (Gemini)"),
}

/**
 * Supported AI models using Koog's pre-defined model configurations
 */
enum class AIModelOption(
  val provider: AIProvider,
  val displayName: String,
  val llModel: LLModel,
) {
  GPT_4O(AIProvider.OPENAI, "GPT-4o", OpenAIModels.Chat.GPT4o),
  GPT_4O_MINI(AIProvider.OPENAI, "GPT-4o Mini", OpenAIModels.CostOptimized.GPT4oMini),
  CLAUDE_SONNET(AIProvider.ANTHROPIC, "Claude 3.5 Sonnet", AnthropicModels.Sonnet_3_5),
  CLAUDE_HAIKU(AIProvider.ANTHROPIC, "Claude 3 Haiku", AnthropicModels.Haiku_3),
  GEMINI_FLASH(AIProvider.GOOGLE, "Gemini 2.5 Flash", GoogleModels.Gemini2_5Flash),
  GEMINI_PRO(AIProvider.GOOGLE, "Gemini 2.5 Pro", GoogleModels.Gemini2_5Pro),
}

/**
 * Service for managing the RevenueCat AI Agent
 */
@Service
class RevenueCatAIAgentService {

  private val systemPrompt = """
    You are a helpful AI assistant specialized in RevenueCat subscription management and analytics.
    You help developers understand their subscription metrics, configure offerings, and troubleshoot
    issues with their RevenueCat integration.

    You have access to tools that can:
    - Fetch current subscription metrics (MRR, active trials, subscriptions, revenue)
    - List and inspect offerings and packages
    - View recent subscriptions
    - Check project configuration status
    - Provide helpful documentation links

    When responding:
    - Be concise and helpful
    - Use markdown formatting for better readability
    - When showing metrics, format numbers appropriately
    - If there's an error, explain what might be wrong and how to fix it
    - If the API is not configured, guide the user to set up their credentials
  """.trimIndent()

  /**
   * Run a query against the AI agent and return the response
   */
  suspend fun runQuery(query: String): Result<String> = withContext(Dispatchers.IO) {
    val settings = RevenueCatSettingsState.getInstance()

    if (!settings.isAIConfigured()) {
      return@withContext Result.failure(
        Exception(
          "AI is not configured. Please set your API key in Settings → RevenueCat → AI Settings.",
        ),
      )
    }

    try {
      val revenueCatTools = RevenueCatTools()
      val toolRegistry = ToolRegistry {
        tools(revenueCatTools.asTools())
      }

      val selectedModel = AIModelOption.entries.find { it.name == settings.aiModel }
        ?: AIModelOption.GPT_4O_MINI

      val executor = when (selectedModel.provider) {
        AIProvider.OPENAI -> simpleOpenAIExecutor(settings.aiApiKey)
        AIProvider.ANTHROPIC -> simpleAnthropicExecutor(settings.aiApiKey)
        AIProvider.GOOGLE -> simpleGoogleAIExecutor(settings.aiApiKey)
      }

      val agent = AIAgent(
        promptExecutor = executor,
        llmModel = selectedModel.llModel,
        systemPrompt = systemPrompt,
        toolRegistry = toolRegistry,
      )

      val result = agent.run(query)
      Result.success(result)
    } catch (e: Exception) {
      Result.failure(Exception("AI Agent error: ${e.message}"))
    }
  }

  companion object {
    fun getInstance(): RevenueCatAIAgentService {
      return ApplicationManager.getApplication().getService(
        RevenueCatAIAgentService::class.java,
      )
    }
  }
}
