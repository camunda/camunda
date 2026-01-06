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
package io.camunda.process.test.impl.dsl.instructions;

import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.assertions.UserTaskSelectors;

/** Factory for creating selector instances from DSL selectors. */
final class InstructionSelectorFactory {

  private InstructionSelectorFactory() {
    // Utility class
  }

  /**
   * Builds a process instance selector from a DSL process instance selector.
   *
   * @param dslSelector the DSL process instance selector
   * @return the process instance selector
   * @throws IllegalArgumentException if the process definition ID is not set
   */
  static ProcessInstanceSelector buildProcessInstanceSelector(
      final io.camunda.process.test.api.dsl.ProcessInstanceSelector dslSelector) {
    return dslSelector
        .getProcessDefinitionId()
        .map(ProcessInstanceSelectors::byProcessId)
        .orElseThrow(
            () -> new IllegalArgumentException("Missing required property: processDefinitionId"));
  }

  /**
   * Builds a user task selector from a DSL user task selector.
   *
   * @param dslSelector the DSL user task selector
   * @return the user task selector
   * @throws IllegalArgumentException if no selector property is set
   */
  static UserTaskSelector buildUserTaskSelector(
      final io.camunda.process.test.api.dsl.UserTaskSelector dslSelector) {
    UserTaskSelector selector = null;

    if (dslSelector.getElementId().isPresent()) {
      selector = UserTaskSelectors.byElementId(dslSelector.getElementId().get());
    }

    if (dslSelector.getTaskName().isPresent()) {
      final UserTaskSelector taskNameSelector =
          UserTaskSelectors.byTaskName(dslSelector.getTaskName().get());
      selector = selector != null ? selector.and(taskNameSelector) : taskNameSelector;
    }

    if (dslSelector.getProcessDefinitionId().isPresent()) {
      final UserTaskSelector processDefSelector =
          UserTaskSelectors.byProcessDefinitionId(dslSelector.getProcessDefinitionId().get());
      selector = selector != null ? selector.and(processDefSelector) : processDefSelector;
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "Missing required property: at least one of elementId, taskName, or processDefinitionId must be set");
    }

    return selector;
  }
}
