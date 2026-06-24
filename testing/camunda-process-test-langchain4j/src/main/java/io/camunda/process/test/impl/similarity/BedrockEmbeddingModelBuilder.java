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

import static io.camunda.process.test.impl.ModelBuilderSupport.require;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel.BedrockTitanEmbeddingModelBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.process.test.impl.BedrockRuntimeClientFactory;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AmazonBedrockConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

final class BedrockEmbeddingModelBuilder {

  public static final String AMAZON_BEDROCK = "amazon-bedrock";
  private static final Logger LOG = LoggerFactory.getLogger(BedrockEmbeddingModelBuilder.class);

  private BedrockEmbeddingModelBuilder() {}

  static EmbeddingModel build(final AmazonBedrockConfig config) {
    LOG.debug("Building Amazon Bedrock embedding model");
    final EmbeddingModel embeddingModel = build(config, BedrockTitanEmbeddingModel.builder());
    LOG.debug(
        "Successfully built Amazon Bedrock embedding model with modelId '{}'", config.getModel());
    return embeddingModel;
  }

  static EmbeddingModel build(
      final AmazonBedrockConfig config, final BedrockTitanEmbeddingModelBuilder builder) {
    final String model = require(config.getModel(), "model", AMAZON_BEDROCK);
    final BedrockRuntimeClient client =
        BedrockRuntimeClientFactory.build(
            config.getRegion(),
            config.getApiKey(),
            config.getCredentialsAccessKey(),
            config.getCredentialsSecretKey(),
            config.getTimeout());
    builder.client(client);
    builder.model(model);
    if (config.getDimensions() != null) {
      LOG.debug("Setting dimensions to {}", config.getDimensions());
      builder.dimensions(config.getDimensions());
    }
    if (config.getNormalize() != null) {
      LOG.debug("Setting normalize to {}", config.getNormalize());
      builder.normalize(config.getNormalize());
    }
    return builder.build();
  }
}
