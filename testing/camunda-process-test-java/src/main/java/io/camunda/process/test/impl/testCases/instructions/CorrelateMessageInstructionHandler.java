/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.CorrelateMessageInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class CorrelateMessageInstructionHandler
    implements TestCaseInstructionHandler<CorrelateMessageInstruction> {

  @Override
  public void execute(
      final CorrelateMessageInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final CorrelateMessageCommandStep3 command;

    if (instruction.getCorrelationKey().isPresent()) {
      command =
          camundaClient
              .newCorrelateMessageCommand()
              .messageName(instruction.getName())
              .correlationKey(instruction.getCorrelationKey().get());
    } else {
      command =
          camundaClient
              .newCorrelateMessageCommand()
              .messageName(instruction.getName())
              .withoutCorrelationKey();
    }

    command.variables(instruction.getVariables());

    command.send().join();
  }

  @Override
  public Class<CorrelateMessageInstruction> getInstructionType() {
    return CorrelateMessageInstruction.class;
  }
}
