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
package io.camunda.process.test.api.assertions;

import java.time.Duration;

/**
 * A base class for assertions to configure the assertion behavior.
 *
 * @param <SELF> the type of the assertion class
 */
public interface WithAssertionConfiguration<SELF> {

  /**
   * Configures the time how long an assertion waits until the expected state is reached. The
   * configuration overrides the global setting for subsequent assertions in this chain. The global
   * setting is not changed.
   *
   * @param assertionTimeout the timeout for subsequent assertions
   * @return the assertion object
   */
  SELF withAssertionTimeout(Duration assertionTimeout);
}
