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

import static io.camunda.process.test.impl.ModelBuilderSupport.require;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AnthropicChatModelBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(AnthropicChatModelBuilder.class);

  private AnthropicChatModelBuilder() {}

  static ChatModel build(final BaseProviderConfig.AnthropicConfig config) {
    LOG.debug("Building Anthropic chat model");

    final String model = require(config.getModel(), "model", "anthropic");
    final String apiKey = require(config.getApiKey(), "apiKey", "anthropic");

    final AnthropicChatModel.AnthropicChatModelBuilder builder =
        AnthropicChatModel.builder().apiKey(apiKey).modelName(model);

    if (config.getTimeout() != null) {
      LOG.debug("Setting timeout to {}", config.getTimeout());
      builder.timeout(config.getTimeout());
    }
    if (config.getTemperature() != null) {
      LOG.debug("Setting temperature to {}", config.getTemperature());
      builder.temperature(config.getTemperature());
    }

    final ChatModel chatModel = builder.build();
    LOG.debug("Successfully built Anthropic chat model with model '{}'", model);
    return chatModel;
  }
}
