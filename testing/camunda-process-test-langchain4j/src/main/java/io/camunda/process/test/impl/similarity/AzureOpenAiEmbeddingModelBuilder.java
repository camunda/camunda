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

import com.azure.identity.DefaultAzureCredentialBuilder;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AzureOpenAiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AzureOpenAiEmbeddingModelBuilder {

  public static final String AZURE_OPENAI = "azure-openai";
  private static final Logger LOG = LoggerFactory.getLogger(AzureOpenAiEmbeddingModelBuilder.class);

  private AzureOpenAiEmbeddingModelBuilder() {}

  static EmbeddingModel build(final AzureOpenAiConfig config) {
    LOG.debug("Building Azure OpenAI embedding model");
    final EmbeddingModel embeddingModel = build(config, AzureOpenAiEmbeddingModel.builder());
    LOG.debug(
        "Successfully built Azure OpenAI embedding model with deploymentName '{}'",
        config.getModel());
    return embeddingModel;
  }

  // visible for testing
  static EmbeddingModel build(
      final AzureOpenAiConfig config, final AzureOpenAiEmbeddingModel.Builder builder) {
    builder.endpoint(require(config.getEndpoint(), "endpoint", AZURE_OPENAI));
    builder.deploymentName(require(config.getModel(), "model", AZURE_OPENAI));
    if (hasText(config.getApiKey())) {
      LOG.debug("Using API key authentication");
      builder.apiKey(config.getApiKey().trim());
    } else {
      LOG.debug(
          "No API key configured, falling back to DefaultAzureCredential "
              + "(environment, workload identity, managed identity, Azure CLI)");
      builder.tokenCredential(new DefaultAzureCredentialBuilder().build());
    }
    if (config.getDimensions() != null) {
      builder.dimensions(config.getDimensions());
    }
    return builder.build();
  }
}
