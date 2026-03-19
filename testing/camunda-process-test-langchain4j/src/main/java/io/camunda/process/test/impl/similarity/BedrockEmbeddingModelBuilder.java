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
package io.camunda.process.test.impl.similarity;

import static io.camunda.process.test.impl.ModelBuilderSupport.hasText;
import static io.camunda.process.test.impl.ModelBuilderSupport.require;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel.BedrockTitanEmbeddingModelBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AmazonBedrockConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

final class BedrockEmbeddingModelBuilder {

  public static final String AMAZON_BEDROCK = "amazon-bedrock";
  private static final Logger LOG = LoggerFactory.getLogger(BedrockEmbeddingModelBuilder.class);

  private BedrockEmbeddingModelBuilder() {}

  static EmbeddingModel build(final AmazonBedrockConfig config) {
    LOG.debug("Building Amazon Bedrock embedding model");

    final String model = require(config.getModel(), "model", AMAZON_BEDROCK);

    final boolean hasAccessKey = hasText(config.getCredentialsAccessKey());
    final boolean hasSecretKey = hasText(config.getCredentialsSecretKey());
    final boolean hasKeyPairAuth = hasAccessKey && hasSecretKey;
    final boolean hasPartialKeyPair = hasAccessKey != hasSecretKey;

    if (hasPartialKeyPair) {
      throw new IllegalStateException(
          "Incomplete key-pair authentication for the 'amazon-bedrock' provider: "
              + "both 'credentialsAccessKey' and 'credentialsSecretKey' must be set together.");
    }

    final BedrockTitanEmbeddingModelBuilder builder =
        BedrockTitanEmbeddingModel.builder().model(model);

    if (hasText(config.getRegion())) {
      LOG.debug("Using configured region '{}'", config.getRegion().trim());
      builder.region(Region.of(config.getRegion().trim()));
    } else {
      LOG.debug("No region configured, falling back to AWS default region resolution");
    }

    if (hasKeyPairAuth) {
      LOG.debug("Using access key / secret key authentication");
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(
                  config.getCredentialsAccessKey().trim(),
                  config.getCredentialsSecretKey().trim())));
    } else {
      LOG.debug("No explicit credentials configured, falling back to AWS default credential chain");
    }

    if (config.getDimensions() != null) {
      builder.dimensions(config.getDimensions());
    }

    if (config.getNormalize() != null) {
      builder.normalize(config.getNormalize());
    }

    final EmbeddingModel embeddingModel = builder.build();
    LOG.debug("Successfully built Amazon Bedrock embedding model with modelId '{}'", model);
    return embeddingModel;
  }
}
