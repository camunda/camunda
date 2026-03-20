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
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AmazonBedrockConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BedrockEmbeddingModelBuilderTest {

  @Test
  void shouldBuildEmbeddingModelWithAccessKeyCredentials() {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(
            "amazon.titan-embed-text-v2:0",
            "us-east-1",
            null,
            "test-access-key",
            "test-secret-key",
            null,
            null);

    // when
    final EmbeddingModel embeddingModel = BedrockEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldBuildEmbeddingModelWithApiKey() {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(
            "amazon.titan-embed-text-v2:0", "us-east-1", "text-api-key", null, null, null, null);

    // when
    final EmbeddingModel embeddingModel = BedrockEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldBuildEmbeddingModelWithoutCredentials(final String nullOrEmpty) {
    // given — no credentials; AWS SDK resolves them from the default credential chain
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(
            "amazon.titan-embed-text-v2:0",
            "us-east-1",
            nullOrEmpty,
            nullOrEmpty,
            nullOrEmpty,
            null,
            null);

    // when
    final EmbeddingModel embeddingModel = BedrockEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldBuildEmbeddingModelWithDimensions() {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(
            "amazon.titan-embed-text-v2:0",
            "us-east-1",
            null,
            "test-access-key",
            "test-secret-key",
            null,
            256);

    // when
    final EmbeddingModel embeddingModel = BedrockEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @Test
  void shouldBuildEmbeddingModelWithNormalize() {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(
            "amazon.titan-embed-text-v2:0",
            "us-east-1",
            null,
            "test-access-key",
            "test-secret-key",
            true,
            null);

    // when
    final EmbeddingModel embeddingModel = BedrockEmbeddingModelBuilder.build(config);

    // then
    assertThat(embeddingModel).isNotNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenModelMissingOrBlank(final String model) {
    // given
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(
            model, "us-east-1", null, "test-access-key", "test-secret-key", null, null);

    // when / then
    assertThatThrownBy(() -> BedrockEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("model")
        .hasMessageContaining(BedrockEmbeddingModelBuilder.AMAZON_BEDROCK);
  }

  @Test
  void shouldThrowWhenOnlyAccessKeySet() {
    // given — partial key pair should fail
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(
            "amazon.titan-embed-text-v2:0", "us-east-1", null, "test-access-key", null, null, null);

    // when / then
    assertThatThrownBy(() -> BedrockEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accessKey")
        .hasMessageContaining("secretKey");
  }

  @Test
  void shouldThrowWhenOnlySecretKeySet() {
    // given — partial key pair should fail
    final AmazonBedrockConfig config =
        new AmazonBedrockConfig(
            "amazon.titan-embed-text-v2:0", "us-east-1", null, null, "test-secret-key", null, null);

    // when / then
    assertThatThrownBy(() -> BedrockEmbeddingModelBuilder.build(config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accessKey")
        .hasMessageContaining("secretKey");
  }
}
