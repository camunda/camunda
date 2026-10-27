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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.OpenAiConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiEmbeddingModelBuilderTest {

  private static final String MODEL = "text-embedding-3-small";
  private static final String API_KEY = "test-api-key";

  @Test
  void shouldBuildEmbeddingModel() {
    // given
    final OpenAiConfig config = new OpenAiConfig(MODEL, API_KEY);

    // when
    final EmbeddingModel embeddingModel = OpenAiEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldSetRequiredFieldsOnBuilder() {
    // given
    final OpenAiConfig config = new OpenAiConfig(MODEL, API_KEY);
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).apiKey(API_KEY);
    verify(mockBuilder).modelName(MODEL);
    verify(mockBuilder, never()).dimensions(any());
    verify(mockBuilder, never()).timeout(any());
  }

  @Test
  void shouldApplyTimeoutToBuilder() {
    // given
    final OpenAiConfig config = new OpenAiConfig(MODEL, API_KEY);
    config.setTimeout(Duration.ofSeconds(30));
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).timeout(Duration.ofSeconds(30));
  }

  @Test
  void shouldApplyDimensionsToBuilder() {
    // given
    final OpenAiConfig config = new OpenAiConfig(MODEL, API_KEY);
    config.setDimensions(512);
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).dimensions(512);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenApiKeyMissingOrBlank(final String apiKey) {
    // given
    final OpenAiConfig config = new OpenAiConfig(MODEL, apiKey);

    // when / then
    assertThatThrownBy(() -> OpenAiEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apiKey")
        .hasMessageContaining(OpenAiEmbeddingModelBuilder.OPENAI);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final OpenAiConfig config = new OpenAiConfig(model, API_KEY);

    // when / then
    assertThatThrownBy(() -> OpenAiEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining(OpenAiEmbeddingModelBuilder.OPENAI);
  }
}
