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

import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.assertions.DecisionSelectors;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.assertions.IncidentSelectors;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.assertions.JobSelectors;
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

  /**
   * Builds a job selector from a DSL job selector.
   *
   * @param dslSelector the DSL job selector
   * @return the job selector
   * @throws IllegalArgumentException if no selector property is set
   */
  static JobSelector buildJobSelector(
      final io.camunda.process.test.api.dsl.JobSelector dslSelector) {
    JobSelector selector = null;

    if (dslSelector.getJobType().isPresent()) {
      selector = JobSelectors.byJobType(dslSelector.getJobType().get());
    }

    if (dslSelector.getElementId().isPresent()) {
      final JobSelector elementIdSelector =
          JobSelectors.byElementId(dslSelector.getElementId().get());
      selector = selector != null ? selector.and(elementIdSelector) : elementIdSelector;
    }

    if (dslSelector.getProcessDefinitionId().isPresent()) {
      final JobSelector processDefSelector =
          JobSelectors.byProcessDefinitionId(dslSelector.getProcessDefinitionId().get());
      selector = selector != null ? selector.and(processDefSelector) : processDefSelector;
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "Missing required property: at least one of jobType, elementId, or processDefinitionId must be set");
    }

    return selector;
  }

  /**
   * Builds an element selector from a DSL element selector.
   *
   * @param dslSelector the DSL element selector
   * @return the element selector
   * @throws IllegalArgumentException if neither elementId nor elementName is set
   */
  static ElementSelector buildElementSelector(
      final io.camunda.process.test.api.dsl.ElementSelector dslSelector) {
    if (dslSelector.getElementId().isPresent()) {
      return ElementSelectors.byId(dslSelector.getElementId().get());
    } else if (dslSelector.getElementName().isPresent()) {
      return ElementSelectors.byName(dslSelector.getElementName().get());
    } else {
      throw new IllegalArgumentException(
          "Element selector must have either elementId or elementName");
    }
  }

  /**
   * Builds an incident selector from a DSL incident selector.
   *
   * @param dslSelector the DSL incident selector
   * @return the incident selector
   * @throws IllegalArgumentException if no selector property is set
   */
  static IncidentSelector buildIncidentSelector(
      final io.camunda.process.test.api.dsl.IncidentSelector dslSelector) {
    IncidentSelector selector = null;

    if (dslSelector.getElementId().isPresent()) {
      selector = IncidentSelectors.byElementId(dslSelector.getElementId().get());
    }

    if (dslSelector.getProcessDefinitionId().isPresent()) {
      final IncidentSelector processDefSelector =
          IncidentSelectors.byProcessDefinitionId(dslSelector.getProcessDefinitionId().get());
      selector = selector != null ? selector.and(processDefSelector) : processDefSelector;
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "Missing required property: at least one of elementId or processDefinitionId must be set");
    }

    return selector;
  }

  /**
   * Builds a decision selector from a DSL decision selector.
   *
   * @param dslSelector the DSL decision selector
   * @return the decision selector
   * @throws IllegalArgumentException if neither decisionDefinitionId nor decisionDefinitionName is
   *     set
   */
  static DecisionSelector buildDecisionSelector(
      final io.camunda.process.test.api.dsl.DecisionSelector dslSelector) {
    DecisionSelector selector = null;

    if (dslSelector.getDecisionDefinitionId().isPresent()) {
      selector = DecisionSelectors.byId(dslSelector.getDecisionDefinitionId().get());
    }

    if (dslSelector.getDecisionDefinitionName().isPresent()) {
      final DecisionSelector nameSelector =
          DecisionSelectors.byName(dslSelector.getDecisionDefinitionName().get());
      selector = selector != null ? selector.and(nameSelector) : nameSelector;
    }

    if (selector == null) {
      throw new IllegalArgumentException(
          "Decision selector must have either decisionDefinitionId or decisionDefinitionName");
    }

    return selector;
  }
}
