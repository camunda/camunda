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
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.testCases.instructions.ThrowBpmnErrorFromJobInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class ThrowBpmnErrorFromJobInstructionHandler
    implements TestCaseInstructionHandler<ThrowBpmnErrorFromJobInstruction> {

  @Override
  public void execute(
      final ThrowBpmnErrorFromJobInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final JobSelector jobSelector =
        InstructionSelectorFactory.buildJobSelector(instruction.getJobSelector());

    if (instruction.getErrorMessage().isPresent()) {
      context.throwBpmnErrorFromJob(
          jobSelector,
          instruction.getErrorCode(),
          instruction.getErrorMessage().get(),
          instruction.getVariables());
    } else {
      context.throwBpmnErrorFromJob(
          jobSelector, instruction.getErrorCode(), instruction.getVariables());
    }
  }

  @Override
  public Class<ThrowBpmnErrorFromJobInstruction> getInstructionType() {
    return ThrowBpmnErrorFromJobInstruction.class;
  }
}
