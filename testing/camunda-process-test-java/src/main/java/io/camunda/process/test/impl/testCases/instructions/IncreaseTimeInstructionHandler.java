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
import io.camunda.process.test.api.testCases.instructions.IncreaseTimeInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.time.Duration;

public class IncreaseTimeInstructionHandler
    implements TestCaseInstructionHandler<IncreaseTimeInstruction> {

  @Override
  public void execute(
      final IncreaseTimeInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final Duration duration = instruction.getDuration();
    context.increaseTime(duration);
  }

  @Override
  public Class<IncreaseTimeInstruction> getInstructionType() {
    return IncreaseTimeInstruction.class;
  }
}
