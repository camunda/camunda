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
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.JudgeConfigBootstrapData;
import io.camunda.process.test.api.judge.JudgeConfigBootstrapProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JudgeConfigBootstrap implements JudgeConfigBootstrapProvider {

  private static final Logger LOG = LoggerFactory.getLogger(JudgeConfigBootstrap.class);

  @Override
  public JudgeConfig bootstrap(final JudgeConfigBootstrapData data) {
    final JudgeConfigBootstrapData.ProviderConfig providerConfig = data.getProviderConfig();
    if (providerConfig == null) {
      LOG.debug("No provider configured, skipping judge config bootstrap");
      return null;
    }

    LOG.debug("Bootstrapping judge config for provider '{}'", providerConfig.getProvider());

    final ChatModel chatModel = createChatModel(providerConfig);
    if (chatModel == null) {
      LOG.debug(
          "Unknown provider '{}', skipping judge config bootstrap", providerConfig.getProvider());
      return null;
    }

    final ChatModelAdapter adapter = chatModel::chat;
    LOG.debug(
        "Judge config bootstrapped successfully for provider '{}' with threshold {}",
        providerConfig.getProvider(),
        data.getThreshold());
    return JudgeConfig.of(adapter, data.getThreshold(), data.getCustomPrompt());
  }

  private ChatModel createChatModel(final JudgeConfigBootstrapData.ProviderConfig providerConfig) {
    if (providerConfig instanceof JudgeConfigBootstrapData.OpenAiConfig openAi) {
      return OpenAiChatModelBuilder.build(openAi);
    } else if (providerConfig instanceof JudgeConfigBootstrapData.AnthropicConfig anthropic) {
      return AnthropicChatModelBuilder.build(anthropic);
    } else if (providerConfig instanceof JudgeConfigBootstrapData.AmazonBedrockConfig bedrock) {
      return BedrockChatModelBuilder.build(bedrock);
    } else if (providerConfig
        instanceof JudgeConfigBootstrapData.OpenAiCompatibleConfig compatible) {
      return OpenAiCompatibleChatModelBuilder.build(compatible);
    } else {
      return null;
    }
  }
}
