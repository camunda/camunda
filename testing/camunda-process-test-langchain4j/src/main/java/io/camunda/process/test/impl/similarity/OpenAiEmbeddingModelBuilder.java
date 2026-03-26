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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpenAiEmbeddingModelBuilder {

  public static final String OPENAI = "openai";
  private static final Logger LOG = LoggerFactory.getLogger(OpenAiEmbeddingModelBuilder.class);

  private OpenAiEmbeddingModelBuilder() {}

  static EmbeddingModel build(final OpenAiConfig config) {
    LOG.debug("Building OpenAI embedding model");

    final String model = require(config.getModel(), "model", OPENAI);
    final String apiKey = require(config.getApiKey(), "apiKey", OPENAI);

    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder =
        OpenAiEmbeddingModel.builder().apiKey(apiKey).modelName(model);

    if (config.getDimensions() != null) {
      builder.dimensions(config.getDimensions());
    }

    final Map<String, String> headers = config.getHeaders();
    if (headers != null && !headers.isEmpty()) {
      builder.customHeaders(headers);
    }

    final EmbeddingModel embeddingModel = builder.build();
    LOG.debug("Successfully built OpenAI embedding model with model '{}'", model);
    return embeddingModel;
  }
}
