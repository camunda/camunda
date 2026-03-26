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

import static io.camunda.process.test.impl.ModelBuilderSupport.hasText;
import static io.camunda.process.test.impl.ModelBuilderSupport.require;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpenAiCompatibleChatModelBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleChatModelBuilder.class);

  private OpenAiCompatibleChatModelBuilder() {}

  static ChatModel build(final BaseProviderConfig.OpenAiCompatibleConfig config) {
    LOG.debug("Building OpenAI-compatible chat model");

    final String model = require(config.getModel(), "model", "openai-compatible");
    final String baseUrl = require(config.getBaseUrl(), "baseUrl", "openai-compatible");

    final OpenAiChatModel.OpenAiChatModelBuilder builder =
        OpenAiChatModel.builder().baseUrl(baseUrl).modelName(model);

    final Map<String, String> headers = config.getHeaders();
    final boolean hasAuthorizationHeader =
        headers != null && headers.keySet().stream().anyMatch("Authorization"::equalsIgnoreCase);
    if (hasText(config.getApiKey())) {
      if (hasAuthorizationHeader) {
        LOG.warn("Both API key and Authorization header are set. The API key will be ignored.");
      } else {
        LOG.debug("Using configured API key");
        builder.apiKey(config.getApiKey().trim());
      }
    }

    if (headers != null && !headers.isEmpty()) {
      builder.customHeaders(headers);
    }

    if (config.getTimeout() != null) {
      LOG.debug("Setting timeout to {}", config.getTimeout());
      builder.timeout(config.getTimeout());
    }
    if (config.getTemperature() != null) {
      LOG.debug("Setting temperature to {}", config.getTemperature());
      builder.temperature(config.getTemperature());
    }

    final ChatModel chatModel = builder.build();
    LOG.debug(
        "Successfully built OpenAI-compatible chat model with baseUrl '{}' and model '{}'",
        baseUrl,
        model);
    return chatModel;
  }
}
