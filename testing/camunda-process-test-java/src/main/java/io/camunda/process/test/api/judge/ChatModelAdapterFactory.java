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

import java.util.Properties;

/** SPI interface for creating a {@link ChatModelAdapter} from properties. */
public interface ChatModelAdapterFactory {

  /**
   * Creates a {@link ChatModelAdapter} from the given properties.
   *
   * <p>Implementations must not throw exceptions. If this factory cannot handle the given
   * properties (e.g. unrecognised provider), return {@code null} so that other factories in the SPI
   * chain can be tried.
   *
   * @param properties the properties to read chat model configuration from
   * @return a configured {@link ChatModelAdapter}, or {@code null} if this factory cannot handle
   *     the given properties
   */
  ChatModelAdapter create(Properties properties);
}
