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

import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.EmbeddingModelAdapterProvider;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class Langchain4jEmbeddingModelAdapterProviderTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("providerConfigurations")
  void shouldCreateAdapterForKnownProvider(
      final String providerName, final EmbeddingModelAdapterProvider provider) {
    assertThat(provider.getProviderName()).isEqualTo(providerName);
  }

  @Test
  void shouldRegisterAllProvidersViaSpi() {
    final ServiceLoader<EmbeddingModelAdapterProvider> providers =
        ServiceLoader.load(
            EmbeddingModelAdapterProvider.class,
            EmbeddingModelAdapterProvider.class.getClassLoader());

    assertThat(providers)
        .extracting(EmbeddingModelAdapterProvider::getProviderName)
        .containsExactlyInAnyOrder("openai", "openai-compatible", "azure-openai", "amazon-bedrock");
  }

  @Test
  void shouldResolveOpenAiProvider() {
    final var config =
        new BaseProviderConfig.OpenAiConfig("text-embedding-3-small", "test-key", null);
    final Optional<EmbeddingModelAdapter> adapter = EmbeddingModelAdapterResolver.resolve(config);
    assertThat(adapter).isPresent();
  }

  @Test
  void shouldResolveBedrockProvider() {
    final var config =
        new BaseProviderConfig.AmazonBedrockConfig(
            "amazon.titan-embed-text-v2:0",
            "us-east-1",
            null,
            "access-key",
            "secret-key",
            null,
            null);
    final Optional<EmbeddingModelAdapter> adapter = EmbeddingModelAdapterResolver.resolve(config);
    assertThat(adapter).isPresent();
  }

  @Test
  void shouldResolveOpenAiCompatibleProvider() {
    final var config =
        new BaseProviderConfig.OpenAiCompatibleConfig(
            "nomic-embed-text", "http://localhost:11434/v1", null, null, null);
    final Optional<EmbeddingModelAdapter> adapter = EmbeddingModelAdapterResolver.resolve(config);
    assertThat(adapter).isPresent();
  }

  @Test
  void shouldResolveAzureOpenAiProvider() {
    final var config =
        new BaseProviderConfig.AzureOpenAiConfig(
            "text-embedding-3-small", "https://my-resource.openai.azure.com/", "test-key", null);
    final Optional<EmbeddingModelAdapter> adapter = EmbeddingModelAdapterResolver.resolve(config);
    assertThat(adapter).isPresent();
  }

  @Test
  void shouldReturnEmptyWhenProviderIsNotRecognised() {
    final var config = new BaseProviderConfig.GenericConfig("unknown-provider", "test-model");
    final Optional<EmbeddingModelAdapter> adapter = EmbeddingModelAdapterResolver.resolve(config);
    assertThat(adapter).isEmpty();
  }

  static Stream<Arguments> providerConfigurations() {
    return Stream.of(
        Arguments.of("openai", new OpenAiEmbeddingModelAdapterProvider()),
        Arguments.of("amazon-bedrock", new BedrockEmbeddingModelAdapterProvider()),
        Arguments.of("openai-compatible", new OpenAiCompatibleEmbeddingModelAdapterProvider()),
        Arguments.of("azure-openai", new AzureOpenAiEmbeddingModelAdapterProvider()));
  }
}
