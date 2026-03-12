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
package io.camunda.process.test.impl.judge;

import io.camunda.process.test.api.judge.ChatModelAdapter;
import io.camunda.process.test.api.judge.ChatModelAdapterProvider;
import io.camunda.process.test.api.judge.ProviderConfig;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Resolves a {@link ChatModelAdapter} from a {@link ProviderConfig} using SPI providers via {@link
 * ServiceLoader}.
 *
 * <p>Iterates all {@link ChatModelAdapterProvider} implementations available on the classpath and
 * returns the first adapter that matches the given config.
 */
public final class ChatModelAdapterResolver {

  /**
   * Resolves a {@link ChatModelAdapter} from the given provider configuration.
   *
   * @param config the provider-specific configuration
   * @return an {@link Optional} containing the resolved {@link ChatModelAdapter}, or {@link
   *     Optional#empty()} if no adapter could be resolved
   */
  public Optional<ChatModelAdapter> resolve(final ProviderConfig config) {
    for (final ChatModelAdapterProvider provider :
        ServiceLoader.load(
            ChatModelAdapterProvider.class, ChatModelAdapterProvider.class.getClassLoader())) {
      final Optional<ChatModelAdapter> adapter = provider.create(config);
      if (adapter.isPresent()) {
        return adapter;
      }
    }
    return Optional.empty();
  }
}
