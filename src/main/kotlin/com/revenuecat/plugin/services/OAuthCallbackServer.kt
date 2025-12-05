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
package com.revenuecat.plugin.services

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Local HTTP server to handle OAuth callback
 */
class OAuthCallbackServer(
  private val port: Int = 48888,
  private val expectedState: String,
) {
  private var server: HttpServer? = null
  private val authCodeFuture = CompletableFuture<OAuthCallbackResult>()

  /**
   * Start the callback server
   */
  fun start() {
    try {
      server = HttpServer.create(InetSocketAddress(port), 0)

      server?.createContext("/oauth/callback") { exchange ->
        try {
          val query = exchange.requestURI.query ?: ""
          val params = parseQueryString(query)

          val code = params["code"]
          val state = params["state"]
          val error = params["error"]
          val errorDescription = params["error_description"]

          // Send response to browser
          val responseHtml = when {
            error != null -> {
              authCodeFuture.complete(OAuthCallbackResult.Error(errorDescription ?: error))
              """
                                <html>
                                <head><title>Authorization Failed</title></head>
                                <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                                    <h1>❌ Authorization Failed</h1>
                                    <p>Error: $errorDescription</p>
                                    <p>You can close this window and return to IntelliJ.</p>
                                </body>
                                </html>
              """.trimIndent()
            }

            code != null && state == expectedState -> {
              authCodeFuture.complete(OAuthCallbackResult.Success(code, state))
              """
                                <html>
                                <head><title>Authorization Successful</title></head>
                                <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                                    <h1>✓ Authorization Successful</h1>
                                    <p>You can now close this window and return to IntelliJ.</p>
                                    <script>window.close();</script>
                                </body>
                                </html>
              """.trimIndent()
            }

            state != expectedState -> {
              authCodeFuture.complete(
                OAuthCallbackResult.Error("State mismatch - possible CSRF attack"),
              )
              """
                                <html>
                                <head><title>Security Error</title></head>
                                <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                                    <h1>❌ Security Error</h1>
                                    <p>State parameter mismatch. This could be a security issue.</p>
                                    <p>Please try again from IntelliJ.</p>
                                </body>
                                </html>
              """.trimIndent()
            }

            else -> {
              authCodeFuture.complete(OAuthCallbackResult.Error("Missing authorization code"))
              """
                                <html>
                                <head><title>Authorization Failed</title></head>
                                <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                                    <h1>❌ Authorization Failed</h1>
                                    <p>Missing authorization code.</p>
                                    <p>Please try again from IntelliJ.</p>
                                </body>
                                </html>
              """.trimIndent()
            }
          }

          val responseBytes = responseHtml.toByteArray()
          exchange.sendResponseHeaders(200, responseBytes.size.toLong())
          exchange.responseBody.use { os ->
            os.write(responseBytes)
          }
        } catch (e: Exception) {
          authCodeFuture.completeExceptionally(e)
        }
      }

      server?.executor = null // Use default executor
      server?.start()
    } catch (e: Exception) {
      authCodeFuture.completeExceptionally(e)
      throw e
    }
  }

  /**
   * Wait for OAuth callback with timeout
   * @param timeoutSeconds Maximum time to wait
   * @return Result of the OAuth callback
   */
  fun waitForCallback(timeoutSeconds: Long = 300): OAuthCallbackResult {
    return try {
      authCodeFuture.get(timeoutSeconds, TimeUnit.SECONDS)
    } catch (e: Exception) {
      OAuthCallbackResult.Error("Timeout waiting for authorization: ${e.message}")
    } finally {
      stop()
    }
  }

  /**
   * Stop the callback server
   */
  fun stop() {
    server?.stop(0)
    server = null
  }

  /**
   * Parse query string into map
   */
  private fun parseQueryString(query: String): Map<String, String> {
    return query.split("&")
      .mapNotNull { param ->
        val parts = param.split("=", limit = 2)
        if (parts.size == 2) {
          parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
        } else {
          null
        }
      }
      .toMap()
  }
}

/**
 * Result of OAuth callback
 */
sealed class OAuthCallbackResult {
  data class Success(val code: String, val state: String) : OAuthCallbackResult()
  data class Error(val message: String) : OAuthCallbackResult()
}
