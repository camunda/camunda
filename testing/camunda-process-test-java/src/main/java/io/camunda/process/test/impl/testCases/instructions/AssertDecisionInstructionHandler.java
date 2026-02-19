/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.testCases.instructions.AssertDecisionInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

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
