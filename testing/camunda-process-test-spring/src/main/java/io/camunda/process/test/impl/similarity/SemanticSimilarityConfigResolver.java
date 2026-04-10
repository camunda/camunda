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

import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.impl.configuration.SemanticSimilarityConfiguration;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationContext;

/**
 * Resolves a {@link SemanticSimilarityConfig} by first checking for Spring beans, then falling back
 * to SPI providers.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>If exactly one {@link EmbeddingModelAdapter} bean exists and no provider is configured, use
 *       it.
 *   <li>If a provider is configured and a bean named {@code "<provider>"} exists, use that bean.
 *   <li>Otherwise, fall back to SPI-based resolution via {@link EmbeddingModelAdapterResolver}.
 * </ol>
 */
public final class SemanticSimilarityConfigResolver {

  private SemanticSimilarityConfigResolver() {}

  public static Optional<SemanticSimilarityConfig> resolve(
      final ApplicationContext applicationContext,
      final SemanticSimilarityConfiguration similarityConfiguration) {
    return resolveAdapter(applicationContext, similarityConfiguration)
        .map(
            adapter -> {
              SemanticSimilarityConfig config =
                  SemanticSimilarityConfig.of(adapter)
                      .withThreshold(similarityConfiguration.getThreshold());
              if (!similarityConfiguration.getPreprocessors().isDefaultsEnabled()) {
                config = config.withoutPreprocessors();
              }
              return config;
            });
  }

  private static Optional<EmbeddingModelAdapter> resolveAdapter(
      final ApplicationContext applicationContext,
      final SemanticSimilarityConfiguration similarityConfiguration) {
    final EmbeddingModelAdapter beanAdapter =
        resolveBeanAdapter(applicationContext, similarityConfiguration);
    if (beanAdapter != null) {
      return Optional.of(beanAdapter);
    }

    if (!similarityConfiguration.hasProviderConfigured()) {
      return Optional.empty();
    }

    final Optional<EmbeddingModelAdapter> spiAdapter =
        EmbeddingModelAdapterResolver.resolve(similarityConfiguration.toProviderConfig());
    if (spiAdapter.isEmpty()) {
      throw new IllegalStateException(
          "Semantic similarity configuration is present but no EmbeddingModelAdapterProvider could be resolved. "
              + "Ensure similarity.embeddingModel.provider is configured and "
              + "the appropriate provider module is on the classpath.");
    }
    return spiAdapter;
  }

  private static EmbeddingModelAdapter resolveBeanAdapter(
      final ApplicationContext applicationContext,
      final SemanticSimilarityConfiguration similarityConfiguration) {
    final Map<String, EmbeddingModelAdapter> beans =
        applicationContext.getBeansOfType(EmbeddingModelAdapter.class);

    if (beans.isEmpty()) {
      return null;
    }

    // Single bean without provider configured: auto-select
    if (beans.size() == 1 && !similarityConfiguration.hasProviderConfigured()) {
      return beans.values().iterator().next();
    }

    // Provider configured: match by provider bean name
    if (similarityConfiguration.hasProviderConfigured()) {
      final String provider = similarityConfiguration.getEmbeddingModel().getProvider().trim();
      return beans.get(provider);
    }

    return null;
  }
}
