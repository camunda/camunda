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
package io.camunda.process.test.api.judge;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Configuration interface that provides the settings needed by a {@link ChatModelAdapterProvider}
 * to create a {@link ChatModelAdapter}. Implementations hold provider-specific details such as the
 * provider name, model identifier, and optional custom properties.
 */
public interface ProviderConfig {

  /** Returns the name of the LLM provider (e.g. {@code "openai"}, {@code "anthropic"}). */
  String getProvider();

  /**
   * Returns the model identifier to use (e.g. {@code "gpt-4o"}, {@code
   * "claude-sonnet-4-20250514"}).
   */
  String getModel();

  /**
   * Returns additional, provider-specific properties as key-value pairs. This allows custom or
   * generic providers to receive arbitrary configuration that is not covered by the standard
   * fields.
   *
   * @return an unmodifiable map of custom properties, or an empty map if none are set
   */
  default Map<String, String> getCustomProperties() {
    return Collections.emptyMap();
  }

  /**
   * Returns the timeout for LLM API calls, or {@code null} to use the provider's default timeout.
   *
   * @return the configured timeout, or {@code null} if not set
   */
  default Duration getTimeout() {
    return null;
  }
}
