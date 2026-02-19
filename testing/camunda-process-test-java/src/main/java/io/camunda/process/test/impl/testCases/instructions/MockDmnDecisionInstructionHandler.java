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
import io.camunda.process.test.api.testCases.instructions.MockDmnDecisionInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class MockDmnDecisionInstructionHandler
    implements TestCaseInstructionHandler<MockDmnDecisionInstruction> {

  @Override
  public void execute(
      final MockDmnDecisionInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    context.mockDmnDecision(instruction.getDecisionDefinitionId(), instruction.getVariables());
  }

  @Override
  public Class<MockDmnDecisionInstruction> getInstructionType() {
    return MockDmnDecisionInstruction.class;
  }
}
