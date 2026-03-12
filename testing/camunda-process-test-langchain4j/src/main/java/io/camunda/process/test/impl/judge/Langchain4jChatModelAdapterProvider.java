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

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.ChatModelAdapterProvider;
import io.camunda.process.test.api.judge.ProviderConfig;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Langchain4jChatModelAdapterProvider implements ChatModelAdapterProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(Langchain4jChatModelAdapterProvider.class);

  @Override
  public Optional<ChatModelAdapter> create(final ProviderConfig config) {
    LOG.debug("Creating chat model adapter for provider '{}'", config.getProvider());

    final ChatModel chatModel = createChatModel(config);
    if (chatModel == null) {
      LOG.debug(
          "Unknown provider '{}', skipping chat model adapter creation", config.getProvider());
      return Optional.empty();
    }

    final ChatModelAdapter adapter = chatModel::chat;
    LOG.debug("Chat model adapter created successfully for provider '{}'", config.getProvider());
    return Optional.of(adapter);
  }

  private ChatModel createChatModel(final ProviderConfig providerConfig) {
    if (providerConfig instanceof ProviderConfig.OpenAiConfig openAi) {
      return OpenAiChatModelBuilder.build(openAi);
    } else if (providerConfig instanceof ProviderConfig.AnthropicConfig anthropic) {
      return AnthropicChatModelBuilder.build(anthropic);
    } else if (providerConfig instanceof ProviderConfig.AmazonBedrockConfig bedrock) {
      return BedrockChatModelBuilder.build(bedrock);
    } else if (providerConfig instanceof ProviderConfig.OpenAiCompatibleConfig compatible) {
      return OpenAiCompatibleChatModelBuilder.build(compatible);
    } else {
      return null;
    }
  }
}
