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

import com.azure.core.credential.TokenCredential;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AzureOpenAiConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AzureOpenAiEmbeddingModelBuilderTest {

  private static final String MODEL = "text-embedding-3-small";
  private static final String ENDPOINT = "https://my-resource.openai.azure.com/";
  private static final String API_KEY = "test-api-key";

  @Test
  void shouldBuildEmbeddingModel() {
    // given
    final AzureOpenAiConfig config = new AzureOpenAiConfig(MODEL, ENDPOINT, API_KEY, null);

    // when
    final EmbeddingModel embeddingModel = AzureOpenAiEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldSetRequiredFieldsOnBuilder() {
    // given
    final AzureOpenAiConfig config = new AzureOpenAiConfig(MODEL, ENDPOINT, API_KEY, null);
    final AzureOpenAiEmbeddingModel.Builder mockBuilder =
        mock(AzureOpenAiEmbeddingModel.Builder.class);

    // when
    AzureOpenAiEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).endpoint(ENDPOINT);
    verify(mockBuilder).deploymentName(MODEL);
    verify(mockBuilder, never()).dimensions(any());
    verify(mockBuilder, never()).timeout(any());
  }

  @Test
  void shouldApplyTimeoutToBuilder() {
    // given
    final AzureOpenAiConfig config = new AzureOpenAiConfig(MODEL, ENDPOINT, API_KEY, null);
    config.setTimeout(Duration.ofSeconds(45));
    final AzureOpenAiEmbeddingModel.Builder mockBuilder =
        mock(AzureOpenAiEmbeddingModel.Builder.class);

    // when
    AzureOpenAiEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).timeout(Duration.ofSeconds(45));
  }

  @Test
  void shouldApplyApiKeyToBuilder() {
    // given
    final AzureOpenAiConfig config = new AzureOpenAiConfig(MODEL, ENDPOINT, API_KEY, null);
    final AzureOpenAiEmbeddingModel.Builder mockBuilder =
        mock(AzureOpenAiEmbeddingModel.Builder.class);

    // when
    AzureOpenAiEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).apiKey(API_KEY);
    verify(mockBuilder, never()).tokenCredential(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldApplyDefaultCredentialsToBuilderWhenNoApiKey(final String apiKey) {
    // given
    final AzureOpenAiConfig config = new AzureOpenAiConfig(MODEL, ENDPOINT, apiKey, null);
    final AzureOpenAiEmbeddingModel.Builder mockBuilder =
        mock(AzureOpenAiEmbeddingModel.Builder.class);

    // when
    AzureOpenAiEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).tokenCredential(any(TokenCredential.class));
    verify(mockBuilder, never()).apiKey(any());
  }

  @Test
  void shouldApplyDimensionsToBuilder() {
    // given
    final AzureOpenAiConfig config = new AzureOpenAiConfig(MODEL, ENDPOINT, API_KEY, 512);
    final AzureOpenAiEmbeddingModel.Builder mockBuilder =
        mock(AzureOpenAiEmbeddingModel.Builder.class);

    // when
    AzureOpenAiEmbeddingModelBuilder.build(config, mockBuilder);

    // then
    verify(mockBuilder).dimensions(512);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenEndpointMissingOrBlank(final String endpoint) {
    // given
    final AzureOpenAiConfig config = new AzureOpenAiConfig(MODEL, endpoint, API_KEY, null);

    // when / then
    assertThatThrownBy(() -> AzureOpenAiEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("endpoint")
        .hasMessageContaining(AzureOpenAiEmbeddingModelBuilder.AZURE_OPENAI);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final AzureOpenAiConfig config = new AzureOpenAiConfig(model, ENDPOINT, API_KEY, null);

    // when / then
    assertThatThrownBy(() -> AzureOpenAiEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining(AzureOpenAiEmbeddingModelBuilder.AZURE_OPENAI);
  }
}
