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
import static org.mockito.Mockito.when;

import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.impl.configuration.SemanticSimilarityConfiguration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class SemanticSimilarityConfigResolverTest {

  private static final EmbeddingModelAdapter ADAPTER_A = text -> new float[] {1.0f};
  private static final EmbeddingModelAdapter ADAPTER_B = text -> new float[] {2.0f};

  @Mock private ApplicationContext applicationContext;

  @Test
  void shouldReturnEmptyWhenNothingConfigured() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class)).thenReturn(Map.of());

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(
            applicationContext, new SemanticSimilarityConfiguration());

    assertThat(result).isEmpty();
  }

  @Test
  void shouldUseSingleBean() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of("myAdapter", ADAPTER_A));

    final SemanticSimilarityConfiguration config = new SemanticSimilarityConfiguration();
    config.setThreshold(0.9);

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getEmbeddingModel()).isSameAs(ADAPTER_A);
    assertThat(result.get().getThreshold()).isEqualTo(0.9);
  }

  @Test
  void shouldUseSingleBeanWithDefaults() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of("myAdapter", ADAPTER_A));

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(
            applicationContext, new SemanticSimilarityConfiguration());

    assertThat(result).isPresent();
    assertThat(result.get().getEmbeddingModel()).isSameAs(ADAPTER_A);
    assertThat(result.get().getThreshold()).isEqualTo(SemanticSimilarityConfig.DEFAULT_THRESHOLD);
    assertThat(result.get().getPreprocessors()).isNotEmpty();
  }

  @Test
  void shouldMatchSingleBeanByNameWhenProviderConfigured() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of("my-adapter", ADAPTER_A));

    final SemanticSimilarityConfiguration config = new SemanticSimilarityConfiguration();
    config.getEmbeddingModel().setProvider("my-adapter");

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getEmbeddingModel()).isSameAs(ADAPTER_A);
  }

  @Test
  void shouldFallBackToSpiWhenSingleBeanAndProviderDoesNotMatch() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of("my-adapter", ADAPTER_A));

    final SemanticSimilarityConfiguration config = new SemanticSimilarityConfiguration();
    config.getEmbeddingModel().setProvider("openai");
    config.getEmbeddingModel().setModel("text-embedding-3-small");
    config.getEmbeddingModel().setApiKey("sk-test");

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getEmbeddingModel()).isNotSameAs(ADAPTER_A);
  }

  @Test
  void shouldSelectBeanByProviderName() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of("my-custom", ADAPTER_A, "another", ADAPTER_B));

    final SemanticSimilarityConfiguration config = new SemanticSimilarityConfiguration();
    config.getEmbeddingModel().setProvider("my-custom");

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getEmbeddingModel()).isSameAs(ADAPTER_A);
  }

  @Test
  void shouldFallBackToSpiWhenMultipleBeansAndNoMatch() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of("custom-a", ADAPTER_A, "custom-b", ADAPTER_B));

    final SemanticSimilarityConfiguration config = new SemanticSimilarityConfiguration();
    config.getEmbeddingModel().setProvider("openai");
    config.getEmbeddingModel().setModel("text-embedding-3-small");
    config.getEmbeddingModel().setApiKey("sk-test");

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getEmbeddingModel()).isNotSameAs(ADAPTER_A).isNotSameAs(ADAPTER_B);
  }

  @Test
  void shouldReturnEmptyWhenMultipleBeansAndNoProvider() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of("custom-a", ADAPTER_A, "custom-b", ADAPTER_B));

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(
            applicationContext, new SemanticSimilarityConfiguration());

    assertThat(result).isEmpty();
  }

  @Test
  void shouldFallBackToSpiWhenNoBeansPresent() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class)).thenReturn(Map.of());

    final SemanticSimilarityConfiguration config = new SemanticSimilarityConfiguration();
    config.getEmbeddingModel().setProvider("openai");
    config.getEmbeddingModel().setModel("text-embedding-3-small");
    config.getEmbeddingModel().setApiKey("sk-test");

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getEmbeddingModel()).isNotNull();
  }

  @Test
  void shouldThrowWhenProviderConfiguredButNotResolvable() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class)).thenReturn(Map.of());

    final SemanticSimilarityConfiguration config = new SemanticSimilarityConfiguration();
    config.getEmbeddingModel().setProvider("unknown-provider");
    config.getEmbeddingModel().setModel("some-model");

    assertThatThrownBy(() -> SemanticSimilarityConfigResolver.resolve(applicationContext, config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no EmbeddingModelAdapterProvider could be resolved");
  }

  @Test
  void shouldDisablePreprocessorsWhenConfigured() {
    when(applicationContext.getBeansOfType(EmbeddingModelAdapter.class))
        .thenReturn(Map.of("myAdapter", ADAPTER_A));

    final SemanticSimilarityConfiguration config = new SemanticSimilarityConfiguration();
    config.getPreprocessors().setDefaultsEnabled(false);

    final Optional<SemanticSimilarityConfig> result =
        SemanticSimilarityConfigResolver.resolve(applicationContext, config);

    assertThat(result).isPresent();
    assertThat(result.get().getPreprocessors()).isEmpty();
  }
}
