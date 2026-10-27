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
package io.camunda.process.test.api.similarity;

/** SPI interface for creating an {@link EmbeddingModelAdapter} from a {@link ProviderConfig}. */
public interface EmbeddingModelAdapterProvider {

  /**
   * Returns the name of the provider this implementation handles (e.g. {@code "openai"}, {@code
   * "azure-openai"}). The resolver uses this name to match the provider against the configured
   * {@link ProviderConfig#getProvider()}.
   *
   * @return the provider name, never {@code null}
   */
  String getProviderName();

  /**
   * Creates an {@link EmbeddingModelAdapter} from the given provider configuration.
   *
   * <p>This method is only called when the configured provider name matches {@link
   * #getProviderName()}. If the configuration is invalid or incomplete, implementations should
   * throw an appropriate runtime exception (for example, an {@link IllegalStateException}) to
   * signal misconfiguration.
   *
   * @param config the provider-specific configuration
   * @return the created {@link EmbeddingModelAdapter}, never {@code null}
   * @throws IllegalStateException if the configuration is invalid or incomplete
   */
  EmbeddingModelAdapter create(ProviderConfig config);
}
