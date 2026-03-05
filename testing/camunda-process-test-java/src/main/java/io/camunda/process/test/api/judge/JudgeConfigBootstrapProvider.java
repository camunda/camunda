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

/** SPI interface for bootstrapping a {@link JudgeConfig} from typed configuration data. */
public interface JudgeConfigBootstrapProvider {

  /**
   * Bootstraps a {@link JudgeConfig} from the given configuration data.
   *
   * <p>Implementations must not throw exceptions for unrecognised or missing provider
   * configuration. Return {@code null} if the data does not contain sufficient judge configuration.
   *
   * @param data the typed configuration data to bootstrap from
   * @return a configured {@link JudgeConfig}, or {@code null} if not configured
   */
  JudgeConfig bootstrap(JudgeConfigurationData data);
}
