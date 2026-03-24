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
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.similarity.BaseProviderConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.OpenAiCompatibleConfig;
import io.camunda.process.test.impl.similarity.EmbeddingModelAdapterResolver;
import io.camunda.process.test.impl.similarity.OpenAiEmbeddingModelAdapterProvider;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;

@NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
public class SemanticSimilarityConfigBootstrapIT {

  static final EmbeddingModelAdapter ADAPTER_A = text -> new float[] {1.0f};
  static final EmbeddingModelAdapter ADAPTER_B = text -> new float[] {0.0f};

  @Configuration
  static class SingleEmbeddingModelAdapterConfig {

    @Bean
    EmbeddingModelAdapter embeddingModelAdapter() {
      return ADAPTER_A;
    }
  }

  @Configuration
  static class GenericEmbeddingModelAdapterConfig {

    @Bean("similarity.my-generic")
    EmbeddingModelAdapter genericEmbeddingModelAdapter() {
      return ADAPTER_A;
    }
  }

  @Configuration
  static class MultipleEmbeddingModelAdapterConfig {

    @Bean("similarity.my-custom")
    EmbeddingModelAdapter customEmbeddingModelAdapter() {
      return ADAPTER_A;
    }

    @Bean("similarity.another-provider")
    EmbeddingModelAdapter anotherEmbeddingModelAdapter() {
      return ADAPTER_B;
    }
  }

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
        "camunda.process-test.similarity.embeddingModel.provider=openai-compatible",
        "camunda.process-test.similarity.embeddingModel.model=nomic-embed-text",
        "camunda.process-test.similarity.embeddingModel.baseUrl=http://localhost:11434/v1",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key",
        "camunda.process-test.similarity.embeddingModel.headers.X-Test-Header=test-header-value"
      })
  @CamundaSpringProcessTest
  class OpenAiCompatibleProviderWithHeaders {

    @Autowired CamundaProcessTestRuntimeConfiguration runtimeConfig;

    @Test
    void shouldBootstrapOpenAiCompatibleProvider() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isNotNull();

      final ProviderConfig providerConfig = runtimeConfig.getSimilarity().toProviderConfig();
      assertThat(providerConfig).isInstanceOf(OpenAiCompatibleConfig.class);
      final Map<String, String> headers = ((OpenAiCompatibleConfig) providerConfig).getHeaders();
      assertThat(headers).isNotNull();
      assertThat(headers).containsEntry("X-Test-Header", "test-header-value");
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
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.threshold=0.75",
      })
  @CamundaSpringProcessTest
  @Import(SemanticSimilarityConfigBootstrapIT.SingleEmbeddingModelAdapterConfig.class)
  class WithSingleEmbeddingModelAdapterBean {

    @Test
    void shouldUseSingleBeanWhenNoProviderConfigured() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isSameAs(ADAPTER_A);
      assertThat(config.getThreshold()).isEqualTo(0.75);
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=my-custom",
        "camunda.process-test.similarity.threshold=0.7",
      })
  @CamundaSpringProcessTest
  @Import(SemanticSimilarityConfigBootstrapIT.MultipleEmbeddingModelAdapterConfig.class)
  class WithMultipleBeansAndMatchingProvider {

    @Test
    void shouldSelectBeanByProviderName() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isSameAs(ADAPTER_A);
      assertThat(config.getThreshold()).isEqualTo(0.7);
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai",
        "camunda.process-test.similarity.embeddingModel.model=text-embedding-3-small",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key",
      })
  @CamundaSpringProcessTest
  @Import(SemanticSimilarityConfigBootstrapIT.MultipleEmbeddingModelAdapterConfig.class)
  class WithMultipleBeansAndNoMatch {

    @Test
    void shouldFallBackToSpiWhenNoBeanMatchesProvider() {
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      // SPI-bootstrapped adapter (from Langchain4j), not one of the beans
      assertThat(config.getEmbeddingModel()).isNotSameAs(ADAPTER_A).isNotSameAs(ADAPTER_B);
    }
  }

  @Nested
  @SpringBootTest(classes = SemanticSimilarityConfigBootstrapIT.class)
  @CamundaSpringProcessTest
  @Import(SemanticSimilarityConfigBootstrapIT.MultipleEmbeddingModelAdapterConfig.class)
  class WithMultipleBeansAndNoProviderProperty {

    @Test
    void shouldNotBootstrapWhenMultipleBeansAndNoProvider() {
      assertThat(CamundaAssert.getSemanticSimilarityConfig()).isNull();
    }
  }

  @Nested
  @SpringBootTest(
      classes = SemanticSimilarityConfigBootstrapIT.class,
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=my-generic",
        "camunda.process-test.similarity.embeddingModel.model=custom-model",
        "camunda.process-test.similarity.embeddingModel.customProperties.endpoint=http://localhost:8080",
        "camunda.process-test.similarity.embeddingModel.customProperties.dimensions=768",
        "camunda.process-test.similarity.threshold=0.6",
      })
  @CamundaSpringProcessTest
  @Import(SemanticSimilarityConfigBootstrapIT.GenericEmbeddingModelAdapterConfig.class)
  class GenericProviderWithCustomProperties {

    @Autowired CamundaProcessTestRuntimeConfiguration runtimeConfig;

    @Test
    void shouldBootstrapAndBindCustomProperties() {
      // similarity config bootstrapped via the single named bean
      final SemanticSimilarityConfig config = CamundaAssert.getSemanticSimilarityConfig();
      assertThat(config).isNotNull();
      assertThat(config.getEmbeddingModel()).isSameAs(ADAPTER_A);
      assertThat(config.getThreshold()).isEqualTo(0.6);

      // custom properties bound to GenericConfig
      final ProviderConfig providerConfig = runtimeConfig.getSimilarity().toProviderConfig();
      assertThat(providerConfig).isInstanceOf(BaseProviderConfig.GenericConfig.class);
      assertThat(providerConfig.getProvider()).isEqualTo("my-generic");
      assertThat(providerConfig.getModel()).isEqualTo("custom-model");
      assertThat(providerConfig.getCustomProperties())
          .containsEntry("endpoint", "http://localhost:8080")
          .containsEntry("dimensions", "768")
          .hasSize(2);
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
      final ProviderConfig config = new BaseProviderConfig.OpenAiConfig(null, null, null);
      final OpenAiEmbeddingModelAdapterProvider provider =
          new OpenAiEmbeddingModelAdapterProvider();
      assertThatThrownBy(() -> provider.create(config)).isInstanceOf(IllegalStateException.class);
    }
  }
}
