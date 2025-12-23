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

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.AssertProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.assertProcessInstance.ProcessInstanceState;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

public class AssertProcessInstanceInstructionHandler
    implements TestCaseInstructionHandler<AssertProcessInstanceInstruction> {

  @Override
  public void execute(
      final AssertProcessInstanceInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    instruction
        .getState()
        .ifPresent(expectedState -> assertState(processInstanceAssert, expectedState));

    instruction
        .hasActiveIncidents()
        .ifPresent(
            hasActiveIncidents -> assertActiveIncidents(processInstanceAssert, hasActiveIncidents));
  }

  @Override
  public Class<AssertProcessInstanceInstruction> getInstructionType() {
    return AssertProcessInstanceInstruction.class;
  }

  private static void assertState(
      final ProcessInstanceAssert processInstanceAssert, final ProcessInstanceState expectedState) {
    switch (expectedState) {
      case IS_ACTIVE:
        processInstanceAssert.isActive();
        break;
      case IS_COMPLETED:
        processInstanceAssert.isCompleted();
        break;
      case IS_TERMINATED:
        processInstanceAssert.isTerminated();
        break;
      case IS_CREATED:
        processInstanceAssert.isCreated();
        break;
      default:
        throw new IllegalArgumentException("Unsupported process instance state: " + expectedState);
    }
  }

  private static void assertActiveIncidents(
      final ProcessInstanceAssert processInstanceAssert, final Boolean hasActiveIncidents) {
    if (hasActiveIncidents) {
      processInstanceAssert.hasActiveIncidents();
    } else {
      processInstanceAssert.hasNoActiveIncidents();
    }
  }
}
