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

import com.azure.identity.DefaultAzureCredentialBuilder;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AzureOpenAiChatModelBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(AzureOpenAiChatModelBuilder.class);

  private AzureOpenAiChatModelBuilder() {}

  static ChatModel build(final BaseProviderConfig.AzureOpenAiConfig config) {
    LOG.debug("Building Azure OpenAI chat model");

    final String model = require(config.getModel(), "model", "azure-openai");
    final String endpoint = require(config.getEndpoint(), "endpoint", "azure-openai");

    final AzureOpenAiChatModel.Builder builder =
        AzureOpenAiChatModel.builder().endpoint(endpoint).deploymentName(model);

    if (hasText(config.getApiKey())) {
      LOG.debug("Using API key authentication");
      builder.apiKey(config.getApiKey().trim());
    } else {
      LOG.debug(
          "No API key configured, falling back to DefaultAzureCredential "
              + "(environment, workload identity, managed identity, Azure CLI)");
      builder.tokenCredential(new DefaultAzureCredentialBuilder().build());
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
        "Successfully built Azure OpenAI chat model with endpoint '{}' and deployment '{}'",
        endpoint,
        model);
    return chatModel;
  }
}
