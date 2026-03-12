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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.ProviderConfig;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig;
import io.camunda.process.test.impl.similarity.EmbeddingModelAdapterResolver;
import io.camunda.process.test.impl.similarity.OpenAiEmbeddingModelAdapterProvider;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;

@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
public class SemanticSimilarityConfigBootstrapIT {

  @Nested
  @SpringBootTest(classes = SemanticSimilarityConfigBootstrapIT.class)
  @CamundaSpringProcessTest
  class NotConfigured {

    @Test
    void shouldNotSetSimilarityConfigWhenNoPropertiesConfigured() {
      assertThat(CamundaAssert.getSemanticSimilarityConfig()).isNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai",
        "camunda.process-test.similarity.embeddingModel.model=text-embedding-3-small",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key"
      })
  @CamundaSpringProcessTest
  class OpenAiProvider {

    @Test
    void shouldBootstrapOpenAiWithDefaultSettings() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isNotNull();
      assertThat(config.getThreshold()).isEqualTo(SemanticSimilarityConfig.DEFAULT_THRESHOLD);
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=amazon-bedrock",
        "camunda.process-test.similarity.embeddingModel.model=amazon.titan-embed-text-v1",
        "camunda.process-test.similarity.embeddingModel.region=us-east-1",
        "camunda.process-test.similarity.embeddingModel.credentials.accessKey=test-access-key",
        "camunda.process-test.similarity.embeddingModel.credentials.secretKey=test-secret-key"
      })
  @CamundaSpringProcessTest
  class BedrockProvider {

    @Test
    void shouldBootstrapBedrockProvider() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai-compatible",
        "camunda.process-test.similarity.embeddingModel.model=nomic-embed-text",
        "camunda.process-test.similarity.embeddingModel.baseUrl=http://localhost:11434/v1",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key"
      })
  @CamundaSpringProcessTest
  class OpenAiCompatibleProvider {

    @Test
    void shouldBootstrapOpenAiCompatibleProvider() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=azure-openai",
        "camunda.process-test.similarity.embeddingModel.model=text-embedding-3-small",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key",
        "camunda.process-test.similarity.embeddingModel.endpoint=https://my-resource.openai.azure.com/"
      })
  @CamundaSpringProcessTest
  class AzureOpenAiProvider {

    @Test
    void shouldBootstrapAzureOpenAiProvider() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isNotNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai",
        "camunda.process-test.similarity.embeddingModel.model=text-embedding-3-small",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key",
        "camunda.process-test.similarity.threshold=0.9"
      })
  @CamundaSpringProcessTest
  class CustomThreshold {

    @Test
    void shouldApplyCustomThreshold() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isNotNull();
      assertThat(config.getThreshold()).isEqualTo(0.9);
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai-compatible",
        "camunda.process-test.similarity.embeddingModel.model=nomic-embed-text",
        "camunda.process-test.similarity.embeddingModel.baseUrl=http://localhost:11434/v1"
      })
  @CamundaSpringProcessTest
  class OpenAiCompatibleWithoutApiKey {

    @Test
    void shouldBootstrapWithoutApiKey() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isNotNull();
    }
  }

  @Nested
  class InvalidConfiguration {

    @Test
    void shouldReturnEmptyWhenProviderNotConfigured() {
      final ProviderConfig config =
          new BaseProviderConfig.GenericConfig("unknown-provider", "test-model");
      final Optional<EmbeddingModelAdapter> adapter = EmbeddingModelAdapterResolver.resolve(config);
      assertThat(adapter).isEmpty();
    }

    @Test
    void shouldThrowWhenRequiredFieldMissing() {
      final ProviderConfig config = new BaseProviderConfig.OpenAiConfig(null, null);
      final OpenAiEmbeddingModelAdapterProvider provider =
          new OpenAiEmbeddingModelAdapterProvider();
      assertThatThrownBy(() -> provider.create(config)).isInstanceOf(IllegalStateException.class);
    }
  }
}
