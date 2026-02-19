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
import io.camunda.process.test.api.testCases.instructions.MockChildProcessInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class MockChildProcessInstructionHandler
    implements TestCaseInstructionHandler<MockChildProcessInstruction> {

  @Override
  public void execute(
      final MockChildProcessInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    context.mockChildProcess(instruction.getProcessDefinitionId(), instruction.getVariables());
  }

  @Override
  public Class<MockChildProcessInstruction> getInstructionType() {
    return MockChildProcessInstruction.class;
  }
}
