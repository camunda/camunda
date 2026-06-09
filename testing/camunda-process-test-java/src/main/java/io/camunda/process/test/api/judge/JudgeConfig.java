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
 * Configuration for the LLM judge used in judge assertions. Instances are immutable; {@code with*}
 * methods return a modified copy.
 */
public interface JudgeConfig {

  /** The default threshold score (0-1) above which a judge evaluation passes. */
  double DEFAULT_THRESHOLD = 0.5;

  /** The default value for {@link #isAttachDocuments()}. */
  boolean DEFAULT_ATTACH_DOCUMENTS = false;

  /**
   * Creates a config with default settings and no chat model. A chat model must be set via {@link
   * #withChatModelAdapter(ChatModelAdapter)} before the config is used for judge evaluations.
   */
  static JudgeConfig defaults() {
    return new JudgeConfigImpl(null, DEFAULT_THRESHOLD, null, DEFAULT_ATTACH_DOCUMENTS);
  }

  /** Creates a config with the given chat model and default settings for everything else. */
  static JudgeConfig of(final ChatModelAdapter chatModel) {
    if (chatModel == null) {
      throw new IllegalArgumentException("chatModel must not be null");
    }
    return new JudgeConfigImpl(chatModel, DEFAULT_THRESHOLD, null, DEFAULT_ATTACH_DOCUMENTS);
  }

  /** Creates a config with the given chat model, threshold and optional custom prompt. */
  static JudgeConfig of(
      final ChatModelAdapter chatModel, final double threshold, final String customPrompt) {
    return of(chatModel).withThreshold(threshold).withCustomPrompt(customPrompt);
  }

  JudgeConfig withChatModelAdapter(ChatModelAdapter chatModel);

  JudgeConfig withThreshold(double threshold);

  /**
   * Replaces only the default evaluation criteria (the "You are an impartial judge..." preamble).
   * The system still controls expectation/value injection, the scoring rubric, and the JSON output
   * format. Pass {@code null} to fall back to the default criteria.
   */
  JudgeConfig withCustomPrompt(String customPrompt);

  /**
   * Toggles whether Camunda document references found in the asserted variable are downloaded and
   * attached to the judge call as structured content blocks. Disabled by default to avoid token
   * cost.
   *
   * <p>Requires the configured {@link ChatModelAdapter} to also implement {@link
   * MultimodalChatModelAdapter}; otherwise attachment is skipped with a warning and the judge sees
   * only the raw variable JSON.
   */
  JudgeConfig withAttachDocuments(boolean attachDocuments);

  ChatModelAdapter getChatModel();

  double getThreshold();

  Optional<String> getCustomPrompt();

  /**
   * @see #withAttachDocuments(boolean)
   */
  boolean isAttachDocuments();
}
