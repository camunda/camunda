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

import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JudgeConfigBootstrap implements JudgeConfigBootstrapProvider {

  private static final Logger LOG = LoggerFactory.getLogger(JudgeConfigBootstrap.class);

  @Override
  public JudgeConfig bootstrap(final JudgeConfigBootstrapData data) {
    final String provider = data.getProvider();
    if (provider == null || provider.trim().isEmpty()) {
      LOG.debug("No provider configured, skipping judge config bootstrap");
      return null;
    }

    final String normalizedProvider = provider.trim().toLowerCase();
    LOG.debug("Bootstrapping judge config for provider '{}'", normalizedProvider);

    final ChatModel chatModel = createChatModel(data, normalizedProvider);
    if (chatModel == null) {
      LOG.debug("Unknown provider '{}', skipping judge config bootstrap", normalizedProvider);
      return null;
    }

    final ChatModelAdapter adapter = chatModel::chat;
    LOG.debug(
        "Judge config bootstrapped successfully for provider '{}' with threshold {}",
        normalizedProvider,
        data.getThreshold());
    return JudgeConfig.of(adapter, data.getThreshold(), data.getCustomPrompt());
  }

  private ChatModel createChatModel(final JudgeConfigBootstrapData data, final String provider) {
    return switch (provider) {
      case "openai" -> OpenAiChatModelBuilder.build(data);
      case "anthropic" -> AnthropicChatModelBuilder.build(data);
      case "amazon-bedrock" -> BedrockChatModelBuilder.build(data);
      case "openai-compatible" -> OpenAiCompatibleChatModelBuilder.build(data);
      default -> null;
    };
  }
}
