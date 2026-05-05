/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api.judge;

import io.camunda.process.test.impl.judge.JudgeConfigImpl;
import java.util.Optional;

/**
 * Configuration for the LLM judge used in judge assertions.
 *
 * <p>Example usage:
 *
 * <pre>
 *   JudgeConfig config = JudgeConfig.of(model::chat)
 *       .withThreshold(0.7)
 *       .withCustomPrompt("You are a domain expert evaluating financial data.");
 *   CamundaAssert.setJudgeConfig(config);
 * </pre>
 */
public interface JudgeConfig {

  /** The default threshold score (0-1) above which a judge evaluation passes. */
  double DEFAULT_THRESHOLD = 0.5;

  /** The default value for {@link #isResolveDocuments()}. */
  boolean DEFAULT_RESOLVE_DOCUMENTS = false;

  /**
   * Creates a new JudgeConfig with default settings and no chat model. A chat model must be set via
   * {@link #withChatModelAdapter(ChatModelAdapter)} before using the config for judge evaluations.
   *
   * @return a new JudgeConfig instance with default settings
   */
  static JudgeConfig defaults() {
    return new JudgeConfigImpl(null, DEFAULT_THRESHOLD, null, DEFAULT_RESOLVE_DOCUMENTS);
  }

  /**
   * Creates a new JudgeConfig with the given chat model and default threshold.
   *
   * @param chatModel the chat model adapter to use for judge evaluations
   * @return a new JudgeConfig instance
   */
  static JudgeConfig of(final ChatModelAdapter chatModel) {
    if (chatModel == null) {
      throw new IllegalArgumentException("chatModel must not be null");
    }
    return new JudgeConfigImpl(chatModel, DEFAULT_THRESHOLD, null, DEFAULT_RESOLVE_DOCUMENTS);
  }

  /**
   * Creates a new JudgeConfig with the given chat model, threshold and optional custom prompt.
   *
   * @param chatModel the chat model adapter to use for judge evaluations
   * @param threshold the threshold score (0-1) above which a judge evaluation passes
   * @param customPrompt the custom evaluation criteria prompt, or {@code null} to use the default
   * @return a new JudgeConfig instance
   */
  static JudgeConfig of(
      final ChatModelAdapter chatModel, final double threshold, final String customPrompt) {
    return of(chatModel).withThreshold(threshold).withCustomPrompt(customPrompt);
  }

  /**
   * Returns a new JudgeConfig with the given chat model adapter, keeping all other settings.
   *
   * @param chatModel the chat model adapter to use for judge evaluations
   * @return a new JudgeConfig instance with the updated chat model
   */
  JudgeConfig withChatModelAdapter(ChatModelAdapter chatModel);

  /**
   * Returns a new JudgeConfig with the given threshold, keeping all other settings.
   *
   * @param threshold the threshold score (0-1) above which a judge evaluation passes
   * @return a new JudgeConfig instance with the updated threshold
   */
  JudgeConfig withThreshold(double threshold);

  /**
   * Returns a new JudgeConfig with the given custom prompt, keeping all other settings. The custom
   * prompt replaces only the default evaluation criteria (the "You are an impartial judge..."
   * preamble). The system still controls the expectation/value injection, scoring rubric, and JSON
   * output format.
   *
   * @param customPrompt the custom evaluation criteria prompt, or {@code null} to use the default
   * @return a new JudgeConfig instance with the custom prompt
   */
  JudgeConfig withCustomPrompt(String customPrompt);

  /**
   * Returns a new JudgeConfig with the given document-resolution toggle, keeping all other
   * settings.
   *
   * <p>When enabled, Camunda document references found in the asserted variable are resolved and
   * their content is provided to the judge as additional context. This allows the judge to reason
   * about binary artifacts (images, PDFs, etc.) that the agent produced as tool call results or
   * received as user input.
   *
   * <p>Disabled by default to avoid unnecessary token cost. Enable on demand for assertions that
   * need to reason about document content.
   *
   * <p>True multimodal evaluation (binary content sent as structured content blocks) is only
   * available when using one of the built-in LangChain4j {@link ChatModelAdapter} providers. When a
   * custom adapter is configured, documents are appended to the prompt as text where possible
   * (text-mime types) and binaries are referenced by metadata only.
   *
   * @param resolveDocuments {@code true} to resolve and include document content
   * @return a new JudgeConfig instance with the updated toggle
   */
  JudgeConfig withResolveDocuments(boolean resolveDocuments);

  /**
   * Returns the chat model adapter, or {@code null} if not yet configured.
   *
   * @return the chat model adapter, or {@code null}
   */
  ChatModelAdapter getChatModel();

  /**
   * Returns the threshold score.
   *
   * @return the threshold score (0-1)
   */
  double getThreshold();

  /**
   * Returns the custom prompt, or empty if the default should be used.
   *
   * @return the custom prompt
   */
  Optional<String> getCustomPrompt();

  /**
   * Returns whether Camunda document references found in the asserted variable should be resolved
   * and their content provided to the judge as additional context.
   *
   * @return {@code true} if document resolution is enabled
   * @see #withResolveDocuments(boolean)
   */
  boolean isResolveDocuments();
}
