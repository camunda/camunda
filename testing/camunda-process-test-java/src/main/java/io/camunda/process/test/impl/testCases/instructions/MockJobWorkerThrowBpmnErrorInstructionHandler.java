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
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerThrowBpmnErrorInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class MockJobWorkerThrowBpmnErrorInstructionHandler
    implements TestCaseInstructionHandler<MockJobWorkerThrowBpmnErrorInstruction> {

  @Override
  public void execute(
      final MockJobWorkerThrowBpmnErrorInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final JobWorkerMockBuilder mockBuilder = context.mockJobWorker(instruction.getJobType());

    if (instruction.getErrorMessage().isPresent()) {
      mockBuilder.thenThrowBpmnError(
          instruction.getErrorCode(),
          instruction.getErrorMessage().get(),
          instruction.getVariables());
    } else {
      mockBuilder.thenThrowBpmnError(instruction.getErrorCode(), instruction.getVariables());
    }
  }

  @Override
  public Class<MockJobWorkerThrowBpmnErrorInstruction> getInstructionType() {
    return MockJobWorkerThrowBpmnErrorInstruction.class;
  }
}
