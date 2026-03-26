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

import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AzureOpenAiConfig;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AzureOpenAiEmbeddingModelBuilder {

  public static final String AZURE_OPENAI = "azure-openai";
  private static final Logger LOG = LoggerFactory.getLogger(AzureOpenAiEmbeddingModelBuilder.class);

  private AzureOpenAiEmbeddingModelBuilder() {}

  static EmbeddingModel build(final AzureOpenAiConfig config) {
    LOG.debug("Building Azure OpenAI embedding model");

    final String endpoint = require(config.getEndpoint(), "endpoint", AZURE_OPENAI);
    final String apiKey = require(config.getApiKey(), "apiKey", AZURE_OPENAI);
    final String deploymentName = require(config.getModel(), "model", AZURE_OPENAI);

    final AzureOpenAiEmbeddingModel.Builder builder =
        AzureOpenAiEmbeddingModel.builder()
            .endpoint(endpoint)
            .apiKey(apiKey)
            .deploymentName(deploymentName);

    if (config.getDimensions() != null) {
      builder.dimensions(config.getDimensions());
    }

    final Map<String, String> headers = config.getHeaders();
    if (headers != null && !headers.isEmpty()) {
      builder.customHeaders(headers);
    }

    final EmbeddingModel embeddingModel = builder.build();
    LOG.debug(
        "Successfully built Azure OpenAI embedding model with deploymentName '{}'", deploymentName);
    return embeddingModel;
  }
}
