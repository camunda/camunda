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
package io.camunda.process.test.utils;

import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.EmbeddingModelAdapterProvider;
import io.camunda.process.test.api.similarity.ProviderConfig;

/**
 * A test-scoped {@link EmbeddingModelAdapterProvider} registered via SPI for verifying
 * ServiceLoader discovery in {@code SemanticSimilarityAssertBootstrapIT}. Always returns an {@link
 * EmbeddingModelAdapter} where every text is embedded as the same unit vector, so any {@code
 * hasVariableSimilarTo} assertion passes without a real embedding model.
 */
public class FakeEmbeddingModelAdapterProvider implements EmbeddingModelAdapterProvider {

  public static final String FAKE_PROVIDER_NAME = "fake-similarity";

  @Override
  public String getProviderName() {
    return FAKE_PROVIDER_NAME;
  }

  @Override
  public EmbeddingModelAdapter create(final ProviderConfig config) {
    // Always return the same unit vector regardless of input → cosine similarity = 1.0
    return text -> new float[] {1.0f, 0.0f};
  }
}
