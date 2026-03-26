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
package io.camunda.process.test.impl.runtime.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.process.test.api.similarity.ProviderConfig;
import io.camunda.process.test.impl.similarity.BaseProviderConfig;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class SemanticSimilarityPropertiesTest {

  @Test
  void shouldReturnDefaults() {
    // given
    final Properties properties = new Properties();

    // when
    final SemanticSimilarityProperties similarityProperties =
        new SemanticSimilarityProperties(properties);

    // then
    assertThat(similarityProperties.getThreshold()).isEqualTo(0.5);
    assertThat(similarityProperties.hasProviderConfigured()).isFalse();
    assertThat(similarityProperties.isDefaultPreprocessorsEnabled()).isTrue();
  }

  @Test
  void shouldReadThreshold() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.threshold", "0.9");

    // when
    final SemanticSimilarityProperties similarityProperties =
        new SemanticSimilarityProperties(properties);

    // then
    assertThat(similarityProperties.getThreshold()).isEqualTo(0.9);
  }

  @Test
  void shouldDetectProviderConfigured() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.embeddingModel.provider", "openai");

    // when
    final SemanticSimilarityProperties similarityProperties =
        new SemanticSimilarityProperties(properties);

    // then
    assertThat(similarityProperties.hasProviderConfigured()).isTrue();
  }

  @Test
  void shouldRejectThresholdAboveOne() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.threshold", "5.0");

    // when / then
    assertThatThrownBy(() -> new SemanticSimilarityProperties(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("similarity.threshold must be between 0.0 and 1.0");
  }

  @Test
  void shouldRejectNegativeThreshold() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.threshold", "-0.1");

    // when / then
    assertThatThrownBy(() -> new SemanticSimilarityProperties(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("similarity.threshold must be between 0.0 and 1.0");
  }

  @Test
  void shouldCreateTypedProviderConfigForKnownProvider() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.embeddingModel.provider", "openai");
    properties.setProperty("similarity.embeddingModel.model", "text-embedding-3-small");
    properties.setProperty("similarity.embeddingModel.apiKey", "sk-test");
    properties.setProperty("similarity.embeddingModel.dimensions", "512");
    properties.setProperty("similarity.embeddingModel.headers.X-Custom", "value");

    // when
    final ProviderConfig config = new SemanticSimilarityProperties(properties).toProviderConfig();

    // then
    assertThat(config).isInstanceOf(BaseProviderConfig.OpenAiConfig.class);
    final BaseProviderConfig.OpenAiConfig openAiConfig = (BaseProviderConfig.OpenAiConfig) config;
    assertThat(openAiConfig.getProvider()).isEqualTo("openai");
    assertThat(openAiConfig.getModel()).isEqualTo("text-embedding-3-small");
    assertThat(openAiConfig.getApiKey()).isEqualTo("sk-test");
    assertThat(openAiConfig.getDimensions()).isEqualTo(512);
    assertThat(openAiConfig.getHeaders()).containsEntry("X-Custom", "value");
  }

  @Test
  void shouldCreateOpenAiCompatibleProviderConfig() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.embeddingModel.provider", "openai-compatible");
    properties.setProperty("similarity.embeddingModel.model", "nomic-embed-text");
    properties.setProperty("similarity.embeddingModel.baseUrl", "http://localhost:11434/v1");
    properties.setProperty("similarity.embeddingModel.apiKey", "ollama");
    properties.setProperty("similarity.embeddingModel.dimensions", "768");

    // when
    final ProviderConfig config = new SemanticSimilarityProperties(properties).toProviderConfig();

    // then
    assertThat(config).isInstanceOf(BaseProviderConfig.OpenAiCompatibleConfig.class);
    final BaseProviderConfig.OpenAiCompatibleConfig compatConfig =
        (BaseProviderConfig.OpenAiCompatibleConfig) config;
    assertThat(compatConfig.getProvider()).isEqualTo("openai-compatible");
    assertThat(compatConfig.getModel()).isEqualTo("nomic-embed-text");
    assertThat(compatConfig.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
    assertThat(compatConfig.getApiKey()).isEqualTo("ollama");
    assertThat(compatConfig.getDimensions()).isEqualTo(768);
  }

  @Test
  void shouldCreateAzureOpenAiProviderConfig() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.embeddingModel.provider", "azure-openai");
    properties.setProperty("similarity.embeddingModel.model", "text-embedding-3-large");
    properties.setProperty(
        "similarity.embeddingModel.endpoint", "https://my-resource.openai.azure.com/");
    properties.setProperty("similarity.embeddingModel.apiKey", "azure-key");
    properties.setProperty("similarity.embeddingModel.dimensions", "1024");

    // when
    final ProviderConfig config = new SemanticSimilarityProperties(properties).toProviderConfig();

    // then
    assertThat(config).isInstanceOf(BaseProviderConfig.AzureOpenAiConfig.class);
    final BaseProviderConfig.AzureOpenAiConfig azureConfig =
        (BaseProviderConfig.AzureOpenAiConfig) config;
    assertThat(azureConfig.getProvider()).isEqualTo("azure-openai");
    assertThat(azureConfig.getModel()).isEqualTo("text-embedding-3-large");
    assertThat(azureConfig.getEndpoint()).isEqualTo("https://my-resource.openai.azure.com/");
    assertThat(azureConfig.getApiKey()).isEqualTo("azure-key");
    assertThat(azureConfig.getDimensions()).isEqualTo(1024);
  }

  @Test
  void shouldCreateGenericConfigForUnknownProvider() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.embeddingModel.provider", "my-custom-embedder");
    properties.setProperty("similarity.embeddingModel.model", "custom-model");

    // when
    final ProviderConfig config = new SemanticSimilarityProperties(properties).toProviderConfig();

    // then
    assertThat(config).isExactlyInstanceOf(BaseProviderConfig.GenericConfig.class);
    assertThat(config.getProvider()).isEqualTo("my-custom-embedder");
    assertThat(config.getModel()).isEqualTo("custom-model");
  }

  @Test
  void shouldPassCustomPropertiesToGenericConfig() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.embeddingModel.provider", "my-custom-embedder");
    properties.setProperty("similarity.embeddingModel.model", "custom-model");
    properties.setProperty(
        "similarity.embeddingModel.customProperties.endpoint", "http://localhost:8080");
    properties.setProperty("similarity.embeddingModel.customProperties.normalize", "true");

    // when
    final ProviderConfig config = new SemanticSimilarityProperties(properties).toProviderConfig();

    // then
    assertThat(config).isExactlyInstanceOf(BaseProviderConfig.GenericConfig.class);
    assertThat(config.getProvider()).isEqualTo("my-custom-embedder");
    assertThat(config.getModel()).isEqualTo("custom-model");
    assertThat(config.getCustomProperties())
        .containsEntry("endpoint", "http://localhost:8080")
        .containsEntry("normalize", "true")
        .hasSize(2);
  }

  @Test
  void shouldCreateAmazonBedrockProviderConfig() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.threshold", "0.7");
    properties.setProperty("similarity.embeddingModel.provider", "amazon-bedrock");
    properties.setProperty("similarity.embeddingModel.model", "amazon.titan-embed-text-v2:0");
    properties.setProperty("similarity.embeddingModel.region", "eu-west-1");
    properties.setProperty("similarity.embeddingModel.credentials.accessKey", "ak");
    properties.setProperty("similarity.embeddingModel.credentials.secretKey", "sk");

    // when
    final SemanticSimilarityProperties similarityProperties =
        new SemanticSimilarityProperties(properties);
    final ProviderConfig config = similarityProperties.toProviderConfig();

    // then
    assertThat(config).isInstanceOf(BaseProviderConfig.AmazonBedrockConfig.class);
    final BaseProviderConfig.AmazonBedrockConfig bedrockConfig =
        (BaseProviderConfig.AmazonBedrockConfig) config;
    assertThat(bedrockConfig.getModel()).isEqualTo("amazon.titan-embed-text-v2:0");
    assertThat(bedrockConfig.getRegion()).isEqualTo("eu-west-1");
    assertThat(bedrockConfig.getCredentialsAccessKey()).isEqualTo("ak");
    assertThat(bedrockConfig.getCredentialsSecretKey()).isEqualTo("sk");
    assertThat(similarityProperties.getThreshold()).isEqualTo(0.7);
  }

  @Test
  void shouldReturnNullProviderConfigWhenNoProviderSet() {
    // given
    final Properties properties = new Properties();

    // when
    final ProviderConfig config = new SemanticSimilarityProperties(properties).toProviderConfig();

    // then
    assertThat(config).isNull();
  }

  @Test
  void shouldTreatPlaceholderAsAbsent() {
    // given
    final Properties properties = new Properties();
    properties.setProperty("similarity.threshold", "${SIMILARITY_THRESHOLD}");
    properties.setProperty("similarity.embeddingModel.provider", "${PROVIDER}");

    // when
    final SemanticSimilarityProperties similarityProperties =
        new SemanticSimilarityProperties(properties);

    // then
    assertThat(similarityProperties.getThreshold()).isEqualTo(0.5);
    assertThat(similarityProperties.hasProviderConfigured()).isFalse();
  }
}
