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

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import io.camunda.process.test.impl.BedrockRuntimeClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

final class BedrockChatModelBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(BedrockChatModelBuilder.class);

  private BedrockChatModelBuilder() {}

  static ChatModel build(final BaseProviderConfig.AmazonBedrockConfig config) {
    LOG.debug("Building Amazon Bedrock chat model");

    final String model = require(config.getModel(), "model", "amazon-bedrock");

    final BedrockRuntimeClient client =
        BedrockRuntimeClientFactory.build(
            config.getRegion(),
            config.getApiKey(),
            config.getCredentialsAccessKey(),
            config.getCredentialsSecretKey());

    final BedrockChatModel.Builder bedrockBuilder =
        BedrockChatModel.builder().client(client).modelId(model);

    if (config.getTimeout() != null) {
      LOG.debug("Setting timeout to {}", config.getTimeout());
      bedrockBuilder.timeout(config.getTimeout());
    }
    if (config.getTemperature() != null) {
      LOG.debug("Setting temperature to {}", config.getTemperature());
      final BedrockChatRequestParameters requestParameters =
          BedrockChatRequestParameters.builder().temperature(config.getTemperature()).build();
      bedrockBuilder.defaultRequestParameters(requestParameters);
    }

    final ChatModel chatModel = bedrockBuilder.build();
    LOG.debug("Successfully built Amazon Bedrock chat model with modelId '{}'", model);
    return chatModel;
  }
}
