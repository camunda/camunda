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
import io.camunda.process.test.api.similarity.EmbeddingModelAdapterProvider;
import io.camunda.process.test.api.similarity.ProviderConfig;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Resolves an {@link EmbeddingModelAdapter} from a {@link ProviderConfig} using SPI providers via
 * {@link ServiceLoader}.
 *
 * <p>Iterates all {@link EmbeddingModelAdapterProvider} implementations available on the classpath
 * and returns the first adapter that matches the given config.
 */
public final class EmbeddingModelAdapterResolver {

  private EmbeddingModelAdapterResolver() {}

  /**
   * Resolves an {@link EmbeddingModelAdapter} from the given provider configuration.
   *
   * <p>Matches the configured {@link ProviderConfig#getProvider()} against each registered {@link
   * EmbeddingModelAdapterProvider#getProviderName()} and delegates creation to the first match.
   *
   * @param config the provider-specific configuration
   * @return an {@link Optional} containing the resolved {@link EmbeddingModelAdapter}, or {@link
   *     Optional#empty()} if no matching provider could be found
   */
  public static Optional<EmbeddingModelAdapter> resolve(final ProviderConfig config) {
    Objects.requireNonNull(config.getProvider(), "config provider must not be null");

    for (final EmbeddingModelAdapterProvider provider :
        ServiceLoader.load(
            EmbeddingModelAdapterProvider.class,
            EmbeddingModelAdapterProvider.class.getClassLoader())) {
      if (Objects.equals(provider.getProviderName(), config.getProvider())) {
        return Optional.of(provider.create(config));
      }
    }
    return Optional.empty();
  }
}
