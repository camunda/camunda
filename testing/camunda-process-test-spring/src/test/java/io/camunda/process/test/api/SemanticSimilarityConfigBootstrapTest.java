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
import io.camunda.process.test.impl.configuration.LegacyCamundaProcessTestRuntimeConfiguration;
import io.camunda.process.test.impl.configuration.SemanticSimilarityConfiguration;
import io.camunda.process.test.impl.similarity.BaseProviderConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AmazonBedrockConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.AzureOpenAiConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.GenericConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.OpenAiCompatibleConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig.OpenAiConfig;
import io.camunda.process.test.impl.similarity.EmbeddingModelAdapterResolver;
import io.camunda.process.test.impl.similarity.OpenAiEmbeddingModelAdapterProvider;
import io.camunda.process.test.impl.similarity.SemanticSimilarityConfigResolver;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties({
  CamundaProcessTestRuntimeConfiguration.class,
  LegacyCamundaProcessTestRuntimeConfiguration.class
})
public class SemanticSimilarityConfigBootstrapTest {

  static final EmbeddingModelAdapter ADAPTER_A = text -> new float[] {1.0f};
  static final EmbeddingModelAdapter ADAPTER_B = text -> new float[] {0.0f};

  @Nested
  class NotConfigured {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldNotResolveConfigWhenNoPropertiesConfigured() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.hasProviderConfigured()).isFalse();

      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai",
        "camunda.process-test.similarity.embeddingModel.model=text-embedding-3-small",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key"
      })
  class OpenAiProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapOpenAiWithDefaultSettings() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold())
          .isEqualTo(SemanticSimilarityConfig.DEFAULT_THRESHOLD);
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> {
                assertThat(embeddingModel.getProvider()).isEqualTo("openai");
                assertThat(embeddingModel.getModel()).isEqualTo("text-embedding-3-small");
                assertThat(embeddingModel.getApiKey()).isEqualTo("test-key");
              });

      // verify provider config creation
      assertThat(similarityConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              OpenAiConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("text-embedding-3-small");
                assertThat(providerConfig.getApiKey()).isEqualTo("test-key");
              });

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getEmbeddingModel()).isNotNull();
                assertThat(config.getThreshold())
                    .isEqualTo(SemanticSimilarityConfig.DEFAULT_THRESHOLD);
              });
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=amazon-bedrock",
        "camunda.process-test.similarity.embeddingModel.model=amazon.titan-embed-text-v1",
        "camunda.process-test.similarity.embeddingModel.region=us-east-1",
        "camunda.process-test.similarity.embeddingModel.credentials.accessKey=test-access-key",
        "camunda.process-test.similarity.embeddingModel.credentials.secretKey=test-secret-key"
      })
  class BedrockProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapBedrockProvider() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold())
          .isEqualTo(SemanticSimilarityConfig.DEFAULT_THRESHOLD);
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> {
                assertThat(embeddingModel.getProvider()).isEqualTo("amazon-bedrock");
                assertThat(embeddingModel.getModel()).isEqualTo("amazon.titan-embed-text-v1");
                assertThat(embeddingModel.getRegion()).isEqualTo("us-east-1");
                assertThat(embeddingModel.getCredentials().getAccessKey())
                    .isEqualTo("test-access-key");
                assertThat(embeddingModel.getCredentials().getSecretKey())
                    .isEqualTo("test-secret-key");
              });

      // verify provider config creation
      assertThat(similarityConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              AmazonBedrockConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("amazon.titan-embed-text-v1");
                assertThat(providerConfig.getRegion()).isEqualTo("us-east-1");
                assertThat(providerConfig.getCredentialsAccessKey()).isEqualTo("test-access-key");
                assertThat(providerConfig.getCredentialsSecretKey()).isEqualTo("test-secret-key");
              });

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getEmbeddingModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai-compatible",
        "camunda.process-test.similarity.embeddingModel.model=nomic-embed-text",
        "camunda.process-test.similarity.embeddingModel.baseUrl=http://localhost:11434/v1",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key"
      })
  class OpenAiCompatibleProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapOpenAiCompatibleProvider() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold())
          .isEqualTo(SemanticSimilarityConfig.DEFAULT_THRESHOLD);
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> {
                assertThat(embeddingModel.getProvider()).isEqualTo("openai-compatible");
                assertThat(embeddingModel.getModel()).isEqualTo("nomic-embed-text");
                assertThat(embeddingModel.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(embeddingModel.getApiKey()).isEqualTo("test-key");
              });

      // verify provider config creation
      assertThat(similarityConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              OpenAiCompatibleConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("nomic-embed-text");
                assertThat(providerConfig.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(providerConfig.getApiKey()).isEqualTo("test-key");
              });

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getEmbeddingModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=azure-openai",
        "camunda.process-test.similarity.embeddingModel.model=text-embedding-3-small",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key",
        "camunda.process-test.similarity.embeddingModel.endpoint=https://my-resource.openai.azure.com/"
      })
  class AzureOpenAiProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapAzureOpenAiProvider() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold())
          .isEqualTo(SemanticSimilarityConfig.DEFAULT_THRESHOLD);
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> {
                assertThat(embeddingModel.getModel()).isEqualTo("text-embedding-3-small");
                assertThat(embeddingModel.getEndpoint())
                    .isEqualTo("https://my-resource.openai.azure.com/");
                assertThat(embeddingModel.getApiKey()).isEqualTo("test-key");
              });

      // verify provider config creation
      assertThat(similarityConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              AzureOpenAiConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("text-embedding-3-small");
                assertThat(providerConfig.getEndpoint())
                    .isEqualTo("https://my-resource.openai.azure.com/");
                assertThat(providerConfig.getApiKey()).isEqualTo("test-key");
              });

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getEmbeddingModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai",
        "camunda.process-test.similarity.embeddingModel.model=text-embedding-3-small",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key",
        "camunda.process-test.similarity.threshold=0.9"
      })
  class CustomThreshold {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldApplyCustomThreshold() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold()).isEqualTo(0.9);
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> {
                assertThat(embeddingModel.getProvider()).isEqualTo("openai");
                assertThat(embeddingModel.getModel()).isEqualTo("text-embedding-3-small");
                assertThat(embeddingModel.getApiKey()).isEqualTo("test-key");
              });

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getEmbeddingModel()).isNotNull();
                assertThat(config.getThreshold()).isEqualTo(0.9);
              });
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai-compatible",
        "camunda.process-test.similarity.embeddingModel.model=nomic-embed-text",
        "camunda.process-test.similarity.embeddingModel.baseUrl=http://localhost:11434/v1"
      })
  class OpenAiCompatibleWithoutApiKey {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapWithoutApiKey() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold())
          .isEqualTo(SemanticSimilarityConfig.DEFAULT_THRESHOLD);
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> {
                assertThat(embeddingModel.getModel()).isEqualTo("nomic-embed-text");
                assertThat(embeddingModel.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(embeddingModel.getApiKey()).isNull();
              });

      // verify provider config creation
      assertThat(similarityConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              OpenAiCompatibleConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getModel()).isEqualTo("nomic-embed-text");
                assertThat(providerConfig.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
                assertThat(providerConfig.getApiKey()).isNull();
              });

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(config -> assertThat(config.getEmbeddingModel()).isNotNull());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.threshold=0.75",
      })
  class WithSingleEmbeddingModelAdapterBean {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldUseSingleBeanWhenNoProviderConfigured() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold()).isEqualTo(0.75);
      assertThat(similarityConfiguration.hasProviderConfigured()).isFalse();

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getEmbeddingModel()).isSameAs(ADAPTER_A);
                assertThat(config.getThreshold()).isEqualTo(0.75);
              });
    }

    @Configuration
    static class SingleEmbeddingModelAdapterConfig {

      @Bean
      EmbeddingModelAdapter embeddingModelAdapter() {
        return ADAPTER_A;
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=my-custom",
        "camunda.process-test.similarity.threshold=0.7",
      })
  class WithMultipleBeansAndMatchingProvider {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldSelectBeanByProviderName() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold()).isEqualTo(0.7);
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> assertThat(embeddingModel.getProvider()).isEqualTo("my-custom"));

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getEmbeddingModel()).isSameAs(ADAPTER_A);
                assertThat(config.getThreshold()).isEqualTo(0.7);
              });
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
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=openai",
        "camunda.process-test.similarity.embeddingModel.model=text-embedding-3-small",
        "camunda.process-test.similarity.embeddingModel.apiKey=test-key",
      })
  class WithMultipleBeansAndNoMatch {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldFallBackToSpiWhenNoBeanMatchesProvider() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> assertThat(embeddingModel.getProvider()).isEqualTo("openai"));

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                // SPI-bootstrapped adapter (from Langchain4j), not one of the beans
                assertThat(config.getEmbeddingModel())
                    .isNotNull()
                    .isNotSameAs(ADAPTER_A)
                    .isNotSameAs(ADAPTER_B);
              });
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
  }

  @Nested
  class WithMultipleBeansAndNoProviderProperty {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldNotBootstrapWhenMultipleBeansAndNoProvider() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.hasProviderConfigured()).isFalse();

      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isEmpty();
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
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.process-test.similarity.embeddingModel.provider=my-generic",
        "camunda.process-test.similarity.embeddingModel.model=custom-model",
        "camunda.process-test.similarity.embeddingModel.customProperties.endpoint=http://localhost:8080",
        "camunda.process-test.similarity.embeddingModel.customProperties.dimensions=768",
        "camunda.process-test.similarity.threshold=0.6",
      })
  class GenericProviderWithCustomProperties {

    @Autowired private CamundaProcessTestRuntimeConfiguration configuration;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void shouldBootstrapAndBindCustomProperties() {
      final SemanticSimilarityConfiguration similarityConfiguration = configuration.getSimilarity();

      // verify properties binding
      assertThat(similarityConfiguration).isNotNull();
      assertThat(similarityConfiguration.getThreshold()).isEqualTo(0.6);
      assertThat(similarityConfiguration.getEmbeddingModel())
          .satisfies(
              embeddingModel -> {
                assertThat(embeddingModel.getProvider()).isEqualTo("my-generic");
                assertThat(embeddingModel.getModel()).isEqualTo("custom-model");
                assertThat(embeddingModel.getCustomProperties())
                    .containsEntry("endpoint", "http://localhost:8080")
                    .containsEntry("dimensions", "768")
                    .hasSize(2);
              });

      // verify provider config creation
      assertThat(similarityConfiguration.toProviderConfig())
          .isInstanceOfSatisfying(
              GenericConfig.class,
              providerConfig -> {
                assertThat(providerConfig.getProvider()).isEqualTo("my-generic");
                assertThat(providerConfig.getModel()).isEqualTo("custom-model");
                assertThat(providerConfig.getCustomProperties())
                    .containsEntry("endpoint", "http://localhost:8080")
                    .containsEntry("dimensions", "768")
                    .hasSize(2);
              });

      // verify similarity config creation
      assertThat(
              SemanticSimilarityConfigResolver.resolve(applicationContext, similarityConfiguration))
          .isNotEmpty()
          .hasValueSatisfying(
              config -> {
                assertThat(config.getEmbeddingModel())
                    .isNotNull()
                    .isSameAs(ADAPTER_A); // single bean selected as provider
                assertThat(config.getThreshold()).isEqualTo(0.6);
              });
    }

    @Configuration
    static class GenericEmbeddingModelAdapterConfig {

      @Bean("similarity.my-generic")
      EmbeddingModelAdapter genericEmbeddingModelAdapter() {
        return ADAPTER_A;
      }
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
