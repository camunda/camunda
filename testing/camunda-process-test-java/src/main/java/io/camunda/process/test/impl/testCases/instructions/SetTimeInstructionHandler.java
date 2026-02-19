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
import io.camunda.process.test.api.testCases.instructions.SetTimeInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.time.Instant;

public class SetTimeInstructionHandler implements TestCaseInstructionHandler<SetTimeInstruction> {

  @Override
  public void execute(
      final SetTimeInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final Instant timeToSet = instruction.getTime();
    context.setTime(timeToSet);
  }

  @Override
  public Class<SetTimeInstruction> getInstructionType() {
    return SetTimeInstruction.class;
  }
}
