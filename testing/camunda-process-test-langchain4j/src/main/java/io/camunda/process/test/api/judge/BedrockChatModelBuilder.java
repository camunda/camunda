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

import static io.camunda.process.test.api.judge.ModelBuilderSupport.require;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.auth.scheme.NoAuthAuthScheme;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

final class BedrockChatModelBuilder {

  private BedrockChatModelBuilder() {}

  static ChatModel build(final JudgeConfigBootstrapData data) {
    final String model = require(data.getModel(), "model", "amazon-bedrock");

    final String region = data.getRegion();
    final String accessKey = data.getCredentialsAccessKey();
    final String secretKey = data.getCredentialsSecretKey();
    final String apiKey = data.getApiKey();

    final boolean hasKeyPairAuth = accessKey != null && secretKey != null;
    final boolean hasApiKeyAuth = apiKey != null;

    if (hasKeyPairAuth && hasApiKeyAuth) {
      throw new IllegalStateException(
          "Ambiguous authentication for the 'amazon-bedrock' provider: "
              + "both accessKey/secretKey and apiKey are set. Use only one authentication method.");
    }

    final BedrockRuntimeClientBuilder clientBuilder = BedrockRuntimeClient.builder();

    if (region != null) {
      clientBuilder.region(Region.of(region));
    }

    if (hasKeyPairAuth) {
      clientBuilder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
    }

    if (hasApiKeyAuth) {
      clientBuilder
          .credentialsProvider(AnonymousCredentialsProvider.create())
          .putAuthScheme(NoAuthAuthScheme.create());

      clientBuilder.overrideConfiguration(
          config -> config.headers(Map.of("Authorization", List.of("Bearer " + apiKey))));
    }

    return BedrockChatModel.builder().client(clientBuilder.build()).modelId(model).build();
  }
}
