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
package io.zeebe.exporter.api.context;

import java.util.Map;

/** Encapsulates the configuration of the exporter. */
public interface Configuration {
  /** @return the configured ID of the exporter */
  String getId();

  /** @return raw map of the parsed arguments from the configuration file */
  Map<String, Object> getArguments();

  /**
   * Helper method to instantiate an object of type {@link T} based on the map of arguments (see
   * {@link Configuration#getArguments()}.
   *
   * <p>Will map argument keys to field names; if no field is present for a key, it will be ignored.
   * If no key is present for a field, it will also be ignored (and retain its initial value).
   *
   * @param configClass class to instantiate
   * @return instantiated configuration class
   */
  <T> T instantiate(Class<T> configClass);
}
