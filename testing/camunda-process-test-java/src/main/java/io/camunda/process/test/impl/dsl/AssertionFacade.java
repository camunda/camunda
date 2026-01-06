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
package io.camunda.process.test.impl.dsl;

import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;

/** Facade to provide assertions for different entities. */
public interface AssertionFacade {

  /**
   * Returns the assertion object for the given process instance selector.
   *
   * @param processInstanceSelector the selector to identify the process instance
   * @return the assertion object
   */
  ProcessInstanceAssert assertThatProcessInstance(
      final ProcessInstanceSelector processInstanceSelector);

  /**
   * Returns the assertion object for the given user task selector.
   *
   * @param userTaskSelector the selector to identify the user task
   * @return the assertion object
   */
  UserTaskAssert assertThatUserTask(final UserTaskSelector userTaskSelector);
}
