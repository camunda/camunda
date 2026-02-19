/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.PublishMessageInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.time.Duration;

public class PublishMessageInstructionHandler
    implements TestCaseInstructionHandler<PublishMessageInstruction> {

  @Override
  public void execute(
      final PublishMessageInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final PublishMessageCommandStep3 command;

    if (instruction.getCorrelationKey().isPresent()) {
      command =
          camundaClient
              .newPublishMessageCommand()
              .messageName(instruction.getName())
              .correlationKey(instruction.getCorrelationKey().get());
    } else {
      command =
          camundaClient
              .newPublishMessageCommand()
              .messageName(instruction.getName())
              .withoutCorrelationKey();
    }

    command.variables(instruction.getVariables());

    instruction.getTimeToLive().ifPresent(ttl -> command.timeToLive(Duration.ofMillis(ttl)));

    instruction.getMessageId().ifPresent(command::messageId);

    command.send().join();
  }

  @Override
  public Class<PublishMessageInstruction> getInstructionType() {
    return PublishMessageInstruction.class;
  }
}
