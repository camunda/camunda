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
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.instructions.AssertVariablesInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;
import java.util.List;
import java.util.Map;

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
    instruction
        .getVariableNames()
        .ifPresent(
            variableNames -> {
              if (hasElementSelector) {
                assertLocalVariableNames(
                    processInstanceAssert,
                    buildElementSelector(instruction),
                    variableNames);
              } else {
                assertVariableNames(processInstanceAssert, variableNames);
              }
            });

    // Assert variables with values
    instruction
        .getVariables()
        .ifPresent(
            variables -> {
              if (hasElementSelector) {
                assertLocalVariables(
                    processInstanceAssert, buildElementSelector(instruction), variables);
              } else {
                assertVariables(processInstanceAssert, variables);
              }
            });
  }

  @Override
  public Class<AssertVariablesInstruction> getInstructionType() {
    return AssertVariablesInstruction.class;
  }

  private ElementSelector buildElementSelector(final AssertVariablesInstruction instruction) {
    final io.camunda.process.test.api.dsl.ElementSelector dslSelector =
        instruction.getElementSelector().orElseThrow();

    if (dslSelector.getElementId().isPresent()) {
      return ElementSelectors.byId(dslSelector.getElementId().get());
    } else if (dslSelector.getElementName().isPresent()) {
      return ElementSelectors.byName(dslSelector.getElementName().get());
    } else {
      throw new IllegalArgumentException(
          "Element selector must have either elementId or elementName");
    }
  }

  private static void assertVariableNames(
      final ProcessInstanceAssert processInstanceAssert, final List<String> variableNames) {
    processInstanceAssert.hasVariableNames(variableNames.toArray(new String[0]));
  }

  private static void assertLocalVariableNames(
      final ProcessInstanceAssert processInstanceAssert,
      final ElementSelector elementSelector,
      final List<String> variableNames) {
    processInstanceAssert.hasLocalVariableNames(
        elementSelector, variableNames.toArray(new String[0]));
  }

  private static void assertVariables(
      final ProcessInstanceAssert processInstanceAssert, final Map<String, Object> variables) {
    processInstanceAssert.hasVariables(variables);
  }

  private static void assertLocalVariables(
      final ProcessInstanceAssert processInstanceAssert,
      final ElementSelector elementSelector,
      final Map<String, Object> variables) {
    processInstanceAssert.hasLocalVariables(elementSelector, variables);
  }
}
