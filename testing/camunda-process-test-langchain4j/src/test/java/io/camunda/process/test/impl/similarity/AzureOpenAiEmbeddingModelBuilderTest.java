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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AzureOpenAiConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AzureOpenAiEmbeddingModelBuilderTest {

  @Test
  void shouldBuildEmbeddingModel() {
    // given
    final AzureOpenAiConfig config =
        new AzureOpenAiConfig(
            "text-embedding-3-small",
            "https://my-resource.openai.azure.com/",
            "test-api-key",
            null,
            null);

    // when
    final EmbeddingModel embeddingModel = AzureOpenAiEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldBuildEmbeddingModelWithDimensions() {
    // given
    final AzureOpenAiConfig config =
        new AzureOpenAiConfig(
            "text-embedding-3-small",
            "https://my-resource.openai.azure.com/",
            "test-api-key",
            512,
            null);

    // when
    final EmbeddingModel embeddingModel = AzureOpenAiEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldBuildEmbeddingModelWithCustomHeaders() {
    // given
    final AzureOpenAiConfig config =
        new AzureOpenAiConfig(
            "text-embedding-3-small",
            "https://my-resource.openai.azure.com/",
            "test-api-key",
            null,
            Map.of("X-Custom-Header", "value"));

    // when
    final EmbeddingModel embeddingModel = AzureOpenAiEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenEndpointMissingOrBlank(final String endpoint) {
    // given
    final AzureOpenAiConfig config =
        new AzureOpenAiConfig("text-embedding-3-small", endpoint, "test-api-key", null, null);

    // when / then
    assertThatThrownBy(() -> AzureOpenAiEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("endpoint")
        .hasMessageContaining(AzureOpenAiEmbeddingModelBuilder.AZURE_OPENAI);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenApiKeyMissingOrBlank(final String apiKey) {
    // given
    final AzureOpenAiConfig config =
        new AzureOpenAiConfig(
            "text-embedding-3-small", "https://my-resource.openai.azure.com/", apiKey, null, null);

    // when / then
    assertThatThrownBy(() -> AzureOpenAiEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apiKey")
        .hasMessageContaining(AzureOpenAiEmbeddingModelBuilder.AZURE_OPENAI);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final AzureOpenAiConfig config =
        new AzureOpenAiConfig(
            model, "https://my-resource.openai.azure.com/", "test-api-key", null, null);

    // when / then
    assertThatThrownBy(() -> AzureOpenAiEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining(AzureOpenAiEmbeddingModelBuilder.AZURE_OPENAI);
  }
}
