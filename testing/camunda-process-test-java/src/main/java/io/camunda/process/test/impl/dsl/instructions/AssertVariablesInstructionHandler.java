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
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.AssertVariablesInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

public class AssertVariablesInstructionHandler
    implements TestCaseInstructionHandler<AssertVariablesInstruction> {

  @Override
  public void execute(
      final AssertVariablesInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    final boolean hasElementSelector = instruction.getElementSelector().isPresent();

    // Assert variable names
    if (!instruction.getVariableNames().isEmpty()) {
      if (hasElementSelector) {
        final ElementSelector elementSelector =
            InstructionSelectorFactory.buildElementSelector(
                instruction.getElementSelector().orElseThrow());
        processInstanceAssert.hasLocalVariableNames(
            elementSelector, instruction.getVariableNames().toArray(new String[0]));
      } else {
        processInstanceAssert.hasVariableNames(
            instruction.getVariableNames().toArray(new String[0]));
      }
    }

    // Assert variables with values
    if (!instruction.getVariables().isEmpty()) {
      if (hasElementSelector) {
        final ElementSelector elementSelector =
            InstructionSelectorFactory.buildElementSelector(
                instruction.getElementSelector().orElseThrow());
        processInstanceAssert.hasLocalVariables(elementSelector, instruction.getVariables());
      } else {
        processInstanceAssert.hasVariables(instruction.getVariables());
      }
    }
  }

  @Override
  public Class<AssertVariablesInstruction> getInstructionType() {
    return AssertVariablesInstruction.class;
  }
}
