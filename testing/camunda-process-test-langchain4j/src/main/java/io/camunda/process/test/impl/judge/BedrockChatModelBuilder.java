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

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.auth.scheme.NoAuthAuthScheme;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

final class BedrockChatModelBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(BedrockChatModelBuilder.class);

  private BedrockChatModelBuilder() {}

  static ChatModel build(final BaseProviderConfig.AmazonBedrockConfig config) {
    LOG.debug("Building Amazon Bedrock chat model");

    final String model = require(config.getModel(), "model", "amazon-bedrock");

    final boolean hasAccessKey = hasText(config.getCredentialsAccessKey());
    final boolean hasSecretKey = hasText(config.getCredentialsSecretKey());
    final boolean hasKeyPairAuth = hasAccessKey && hasSecretKey;
    final boolean hasPartialKeyPair = hasAccessKey != hasSecretKey;
    final boolean hasApiKeyAuth = hasText(config.getApiKey());

    if (hasPartialKeyPair) {
      throw new IllegalStateException(
          "Incomplete key-pair authentication for the 'amazon-bedrock' provider: "
              + "both 'accessKey' and 'secretKey' must be set together.");
    }

    if (hasKeyPairAuth && hasApiKeyAuth) {
      throw new IllegalStateException(
          "Ambiguous authentication for the 'amazon-bedrock' provider: "
              + "both accessKey/secretKey and apiKey are set. Use only one authentication method.");
    }

    final BedrockRuntimeClientBuilder clientBuilder = BedrockRuntimeClient.builder();

    if (hasText(config.getRegion())) {
      LOG.debug("Using configured region '{}'", config.getRegion().trim());
      clientBuilder.region(Region.of(config.getRegion().trim()));
    } else {
      LOG.debug("No region configured, falling back to AWS default region resolution");
    }

    if (hasKeyPairAuth) {
      LOG.debug("Using access key / secret key authentication");
      clientBuilder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(
                  config.getCredentialsAccessKey().trim(),
                  config.getCredentialsSecretKey().trim())));
    } else if (hasApiKeyAuth) {
      LOG.debug("Using API key (Bearer token) authentication");
      clientBuilder
          .credentialsProvider(AnonymousCredentialsProvider.create())
          .putAuthScheme(NoAuthAuthScheme.create());

      clientBuilder.overrideConfiguration(
          cfg ->
              cfg.headers(Map.of("Authorization", List.of("Bearer " + config.getApiKey().trim()))));
    } else {
      LOG.debug("No explicit credentials configured, falling back to AWS default credential chain");
    }

    final BedrockChatModel.Builder bedrockBuilder =
        BedrockChatModel.builder().client(clientBuilder.build()).modelId(model);

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
