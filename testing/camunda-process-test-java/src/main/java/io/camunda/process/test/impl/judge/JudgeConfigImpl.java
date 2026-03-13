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
package io.camunda.process.test.impl.judge;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.JudgeConfig;
import java.util.Optional;

public final class JudgeConfigImpl implements JudgeConfig {

  private final ChatModelAdapter chatModel;
  private final double threshold;
  private final String customPrompt;

  public JudgeConfigImpl(
      final ChatModelAdapter chatModel, final double threshold, final String customPrompt) {
    this.chatModel = chatModel;
    this.threshold = threshold;
    this.customPrompt = customPrompt;
  }

  @Override
  public JudgeConfig withChatModelAdapter(final ChatModelAdapter chatModel) {
    if (chatModel == null) {
      throw new IllegalArgumentException("chatModel must not be null");
    }
    return new JudgeConfigImpl(chatModel, threshold, customPrompt);
  }

  @Override
  public JudgeConfig withThreshold(final double threshold) {
    if (threshold < 0.0 || threshold > 1.0) {
      throw new IllegalArgumentException(
          "threshold must be between 0.0 and 1.0, was: " + threshold);
    }
    return new JudgeConfigImpl(chatModel, threshold, customPrompt);
  }

  @Override
  public JudgeConfig withCustomPrompt(final String customPrompt) {
    return new JudgeConfigImpl(chatModel, threshold, customPrompt);
  }

  @Override
  public ChatModelAdapter getChatModel() {
    return chatModel;
  }

  @Override
  public double getThreshold() {
    return threshold;
  }

  @Override
  public Optional<String> getCustomPrompt() {
    return Optional.ofNullable(customPrompt);
  }
}
