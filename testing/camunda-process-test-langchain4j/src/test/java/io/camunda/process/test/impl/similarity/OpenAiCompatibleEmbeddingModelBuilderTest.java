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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.OpenAiCompatibleConfig;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiCompatibleEmbeddingModelBuilderTest {

  private static final String MODEL = "nomic-embed-text";
  private static final String BASE_URL = "http://localhost:11434/v1";
  private static final String API_KEY = "test-api-key";

  @Test
  void shouldBuildEmbeddingModel() {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(MODEL, BASE_URL, API_KEY, null, null);

    // when
    final EmbeddingModel embeddingModel = OpenAiCompatibleEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldSetRequiredFieldsOnBuilder() {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(MODEL, BASE_URL, null, null, null);
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiCompatibleEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).baseUrl(BASE_URL);
    verify(mockBuilder).modelName(MODEL);
    verify(mockBuilder, never()).apiKey(any());
    verify(mockBuilder, never()).customHeaders(anyMap());
    verify(mockBuilder, never()).dimensions(any());
    verify(mockBuilder, never()).timeout(any());
  }

  @Test
  void shouldApplyTimeoutToBuilder() {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(MODEL, BASE_URL, null, null, null);
    config.setTimeout(Duration.ofSeconds(30));
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiCompatibleEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).timeout(Duration.ofSeconds(30));
  }

  @Test
  void shouldApplyApiKeyToBuilderWhenNoAuthorizationHeader() {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(MODEL, BASE_URL, API_KEY, null, null);
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiCompatibleEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).apiKey(API_KEY);
  }

  @Test
  void shouldNotApplyApiKeyWhenAuthorizationHeaderPresent() {
    // given
    final Map<String, String> headers = Map.of("Authorization", "Bearer test-token");
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(MODEL, BASE_URL, API_KEY, null, headers);
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiCompatibleEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder, never()).apiKey(any());
    verify(mockBuilder).customHeaders(headers);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldNotApplyApiKeyToBuilderWhenNullOrBlank(final String apiKey) {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(MODEL, BASE_URL, apiKey, null, null);
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiCompatibleEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder, never()).apiKey(any());
  }

  @Test
  void shouldApplyHeadersToBuilder() {
    // given
    final Map<String, String> headers = Map.of("X-Custom-Header", "value");
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(MODEL, BASE_URL, null, null, headers);
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiCompatibleEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).customHeaders(headers);
  }

  @Test
  void shouldApplyDimensionsToBuilder() {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(MODEL, BASE_URL, null, 768, null);
    final OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder mockBuilder =
        mock(OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder.class);

    // when
    OpenAiCompatibleEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).dimensions(768);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final OpenAiCompatibleConfig config =
        new OpenAiCompatibleConfig(model, BASE_URL, null, null, null);

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
        new OpenAiCompatibleConfig(MODEL, baseUrl, null, null, null);

    // when / then
    assertThatThrownBy(() -> OpenAiCompatibleEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("baseUrl")
        .hasMessageContaining(OpenAiCompatibleEmbeddingModelBuilder.OPENAI_COMPATIBLE);
  }
}
