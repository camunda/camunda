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
import io.camunda.process.test.impl.similarity.BaseProviderConfig.OpenAiCompatibleConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiCompatibleEmbeddingModelBuilderTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldBuildEmbeddingModelWithoutApiKey(final String apiKey) {
    // given — null or blank apiKey is treated as absent
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(
            "nomic-embed-text", "http://localhost:11434/v1", apiKey, null, null);

    // when
    final EmbeddingModel embeddingModel = OpenAiCompatibleEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldBuildEmbeddingModelWithOptionalFields() {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(
            "nomic-embed-text",
            "http://localhost:11434/v1",
            "optional-key",
            768,
            Map.of("X-Custom-Header", "value"));

    // when
    final EmbeddingModel embeddingModel = OpenAiCompatibleEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(model, "http://localhost:11434/v1", null, null, null);

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining(OpenAiCompatibleEmbeddingModelBuilder.OPENAI_COMPATIBLE);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenBaseUrlMissingOrBlank(final String baseUrl) {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig("nomic-embed-text", baseUrl, null, null, null);

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("baseUrl")
        .hasMessageContaining(OpenAiCompatibleEmbeddingModelBuilder.OPENAI_COMPATIBLE);
  }
}
