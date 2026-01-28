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
import io.camunda.process.test.api.dsl.instructions.AssertElementInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.assertElementInstance.ElementInstanceState;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

public class AssertElementInstanceInstructionHandler
    implements TestCaseInstructionHandler<AssertElementInstanceInstruction> {

  @Override
  public void execute(
      final AssertElementInstanceInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ElementSelector elementSelector =
        InstructionSelectorFactory.buildElementSelector(instruction.getElementSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    final int amount = instruction.getAmount();
    final ElementInstanceState state = instruction.getState();

    assertElementState(processInstanceAssert, elementSelector, state, amount);
  }

  @Override
  public Class<AssertElementInstanceInstruction> getInstructionType() {
    return AssertElementInstanceInstruction.class;
  }

  private static void assertElementState(
      final ProcessInstanceAssert processInstanceAssert,
      final ElementSelector elementSelector,
      final ElementInstanceState state,
      final int amount) {
    switch (state) {
      case IS_ACTIVE:
        processInstanceAssert.hasActiveElement(elementSelector, amount);
        break;
      case IS_COMPLETED:
        processInstanceAssert.hasCompletedElement(elementSelector, amount);
        break;
      case IS_TERMINATED:
        processInstanceAssert.hasTerminatedElement(elementSelector, amount);
        break;
      default:
        throw new IllegalArgumentException("Unsupported element instance state: " + state);
    }
  }
}
