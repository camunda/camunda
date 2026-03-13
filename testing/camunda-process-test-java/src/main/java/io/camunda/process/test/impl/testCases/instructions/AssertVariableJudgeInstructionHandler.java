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
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.testCases.instructions.AssertVariableJudgeInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.util.Optional;

public class AssertVariableJudgeInstructionHandler
    implements TestCaseInstructionHandler<AssertVariableJudgeInstruction> {

  @Override
  public void execute(
      final AssertVariableJudgeInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    // Not declared final because withJudgeConfig returns a new assertion instance
    ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    if (instruction.getThreshold().isPresent() || instruction.getCustomPrompt().isPresent()) {
      processInstanceAssert =
          processInstanceAssert.withJudgeConfig(
              config -> {
                JudgeConfig modified = config;
                if (instruction.getThreshold().isPresent()) {
                  modified = modified.withThreshold(instruction.getThreshold().get());
                }
                if (instruction.getCustomPrompt().isPresent()) {
                  modified = modified.withCustomPrompt(instruction.getCustomPrompt().get());
                }
                return modified;
              });
    }

    final Optional<ElementSelector> elementSelector =
        instruction.getElementSelector().map(InstructionSelectorFactory::buildElementSelector);

    if (elementSelector.isPresent()) {
      processInstanceAssert.hasLocalVariableSatisfiesJudge(
          elementSelector.get(), instruction.getVariableName(), instruction.getExpectation());
    } else {
      processInstanceAssert.hasVariableSatisfiesJudge(
          instruction.getVariableName(), instruction.getExpectation());
    }
  }

  @Override
  public Class<AssertVariableJudgeInstruction> getInstructionType() {
    return AssertVariableJudgeInstruction.class;
  }
}
