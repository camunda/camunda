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
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.IncidentAssert;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.testCases.instructions.AssertIncidentInstruction;
import io.camunda.process.test.api.testCases.instructions.assertIncident.IncidentState;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class AssertIncidentInstructionHandler
    implements TestCaseInstructionHandler<AssertIncidentInstruction> {

  @Override
  public void execute(
      final AssertIncidentInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final IncidentSelector incidentSelector =
        InstructionSelectorFactory.buildIncidentSelector(instruction.getIncidentSelector());
    final IncidentAssert incidentAssert = assertionFacade.assertThatIncident(incidentSelector);

    instruction.getState().ifPresent(expectedState -> assertState(incidentAssert, expectedState));
    instruction
        .getErrorType()
        .map(errorType -> IncidentErrorType.valueOf(errorType.name()))
        .ifPresent(incidentAssert::hasErrorType);
    instruction.getErrorMessage().ifPresent(incidentAssert::hasErrorMessage);
    instruction.getElementId().ifPresent(incidentAssert::hasElementId);
  }

  @Override
  public Class<AssertIncidentInstruction> getInstructionType() {
    return AssertIncidentInstruction.class;
  }

  private static void assertState(
      final IncidentAssert incidentAssert, final IncidentState expectedState) {
    switch (expectedState) {
      case IS_ACTIVE:
        incidentAssert.isActive();
        break;
      case IS_RESOLVED:
        incidentAssert.isResolved();
        break;
      default:
        throw new IllegalArgumentException("Unsupported incident state: " + expectedState);
    }
  }
}
