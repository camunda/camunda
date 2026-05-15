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
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.AssertVariableSimilarToInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.util.Optional;

public class AssertVariableSimilarToInstructionHandler
    implements TestCaseInstructionHandler<AssertVariableSimilarToInstruction> {

  @Override
  public void execute(
      final AssertVariableSimilarToInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    final Optional<ElementSelector> elementSelector =
        instruction.getElementSelector().map(InstructionSelectorFactory::buildElementSelector);

    final String variableName = instruction.getVariableName();
    final String expectedValue = instruction.getExpectedValue();

    if (elementSelector.isPresent()) {
      if (instruction.getThreshold().isPresent()) {
        processInstanceAssert
            .withSemanticSimilarityConfig(
                config -> config.withThreshold(instruction.getThreshold().get()))
            .hasLocalVariableSimilarTo(elementSelector.get(), variableName, expectedValue);
      } else {
        processInstanceAssert.hasLocalVariableSimilarTo(
            elementSelector.get(), variableName, expectedValue);
      }
    } else {
      if (instruction.getThreshold().isPresent()) {
        processInstanceAssert
            .withSemanticSimilarityConfig(
                config -> config.withThreshold(instruction.getThreshold().get()))
            .hasVariableSimilarTo(variableName, expectedValue);
      } else {
        processInstanceAssert.hasVariableSimilarTo(variableName, expectedValue);
      }
    }
  }

  @Override
  public Class<AssertVariableSimilarToInstruction> getInstructionType() {
    return AssertVariableSimilarToInstruction.class;
  }
}
