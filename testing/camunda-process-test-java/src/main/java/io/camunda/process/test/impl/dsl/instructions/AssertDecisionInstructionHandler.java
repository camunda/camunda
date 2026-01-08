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
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.dsl.instructions.AssertDecisionInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

public class AssertDecisionInstructionHandler
    implements TestCaseInstructionHandler<AssertDecisionInstruction> {

  @Override
  public void execute(
      final AssertDecisionInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final DecisionSelector decisionSelector =
        InstructionSelectorFactory.buildDecisionSelector(instruction.getDecisionSelector());

    final DecisionInstanceAssert decisionAssert =
        assertionFacade.assertThatDecision(decisionSelector);

    // Assert output if specified
    instruction.getOutput().ifPresent(decisionAssert::hasOutput);

    // Assert matched rules if specified
    if (!instruction.getMatchedRules().isEmpty()) {
      final int[] matchedRules =
          instruction.getMatchedRules().stream().mapToInt(Integer::intValue).toArray();
      decisionAssert.hasMatchedRules(matchedRules);
    }

    // Assert not matched rules if specified
    if (!instruction.getNotMatchedRules().isEmpty()) {
      final int[] notMatchedRules =
          instruction.getNotMatchedRules().stream().mapToInt(Integer::intValue).toArray();
      decisionAssert.hasNotMatchedRules(notMatchedRules);
    }

    // Assert no matched rules if specified
    if (instruction.getNoMatchedRules()) {
      decisionAssert.hasNoMatchedRules();
    }
  }

  @Override
  public Class<AssertDecisionInstruction> getInstructionType() {
    return AssertDecisionInstruction.class;
  }
}
