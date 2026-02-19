/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.TestCaseInstruction;

/**
 * Implements the behavior of a specific {@link TestCaseInstruction}.
 *
 * @param <T> the type of the instruction
 */
public interface TestCaseInstructionHandler<T extends TestCaseInstruction> {

  /**
   * Executes the given instruction.
   *
   * @param instruction the instruction to execute
   * @param context the test context with utilities
   * @param camundaClient the Camunda client to send commands
   * @param assertionFacade the facade to perform assertions
   */
  void execute(
      final T instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade);

  /**
   * Gets the type of instruction this handler can execute.
   *
   * @return the class of the instruction type
   */
  Class<T> getInstructionType();
}
