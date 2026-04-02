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

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.OpenAiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpenAiEmbeddingModelBuilder {

  public static final String OPENAI = "openai";
  private static final Logger LOG = LoggerFactory.getLogger(OpenAiEmbeddingModelBuilder.class);

  private OpenAiEmbeddingModelBuilder() {}

  static EmbeddingModel build(final OpenAiConfig config) {
    LOG.debug("Building OpenAI embedding model");
    final EmbeddingModel embeddingModel = build(config, OpenAiEmbeddingModel.builder());
    LOG.debug("Successfully built OpenAI embedding model with model '{}'", config.getModel());
    return embeddingModel;
  }

  static EmbeddingModel build(
      final OpenAiConfig config, final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder) {
    builder.apiKey(require(config.getApiKey(), "apiKey", OPENAI));
    builder.modelName(require(config.getModel(), "model", OPENAI));
    if (config.getTimeout() != null) {
      LOG.debug("Setting timeout to {}", config.getTimeout());
      builder.timeout(config.getTimeout());
    }
    if (config.getDimensions() != null) {
      LOG.debug("Setting dimensions to {}", config.getDimensions());
      builder.dimensions(config.getDimensions());
    }
    return builder.build();
  }
}
