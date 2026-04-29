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
import io.camunda.process.test.api.testCases.instructions.AssertVariableInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertVariableInstruction.SatisfiesJudge;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.util.Optional;

public class AssertVariableInstructionHandler
    implements TestCaseInstructionHandler<AssertVariableInstruction> {

  @Override
  public Class<AssertVariableInstruction> getInstructionType() {
    return AssertVariableInstruction.class;
  }

  @Override
  public void execute(
      final AssertVariableInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());
    final Optional<ElementSelector> elementSelector =
        instruction.getElementSelector().map(InstructionSelectorFactory::buildElementSelector);
    final ProcessInstanceAssert baseAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);
    final String variableName = instruction.getVariableName();

    instruction
        .getSatisfiesJudge()
        .ifPresent(judge -> applyJudgeAssertion(baseAssert, variableName, elementSelector, judge));
  }

  private static void applyJudgeAssertion(
      final ProcessInstanceAssert baseAssert,
      final String variableName,
      final Optional<ElementSelector> elementSelector,
      final SatisfiesJudge satisfiesJudge) {

    final ProcessInstanceAssert configured = applyJudgeConfigOverrides(baseAssert, satisfiesJudge);
    final String expectation = satisfiesJudge.getExpectation();

    if (elementSelector.isPresent()) {
      configured.hasLocalVariableSatisfiesJudge(elementSelector.get(), variableName, expectation);
    } else {
      configured.hasVariableSatisfiesJudge(variableName, expectation);
    }
  }

  private static ProcessInstanceAssert applyJudgeConfigOverrides(
      final ProcessInstanceAssert baseAssert, final SatisfiesJudge satisfiesJudge) {
    if (!satisfiesJudge.getThreshold().isPresent()
        && !satisfiesJudge.getCustomPrompt().isPresent()) {
      return baseAssert;
    }
    return baseAssert.withJudgeConfig(
        config -> {
          JudgeConfig modified = config;
          if (satisfiesJudge.getThreshold().isPresent()) {
            modified = modified.withThreshold(satisfiesJudge.getThreshold().get());
          }
          if (satisfiesJudge.getCustomPrompt().isPresent()) {
            modified = modified.withCustomPrompt(satisfiesJudge.getCustomPrompt().get());
          }
          return modified;
        });
  }
}
