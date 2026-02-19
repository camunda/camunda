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
import io.camunda.process.test.api.testCases.instructions.EvaluateDecisionInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class EvaluateDecisionInstructionHandler
    implements TestCaseInstructionHandler<EvaluateDecisionInstruction> {

  @Override
  public void execute(
      final EvaluateDecisionInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final String decisionDefinitionId =
        instruction
            .getDecisionDefinitionSelector()
            .getDecisionDefinitionId()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Missing required property: decisionDefinitionId"));

    camundaClient
        .newEvaluateDecisionCommand()
        .decisionId(decisionDefinitionId)
        .variables(instruction.getVariables())
        .send()
        .join();
  }

  @Override
  public Class<EvaluateDecisionInstruction> getInstructionType() {
    return EvaluateDecisionInstruction.class;
  }
}
