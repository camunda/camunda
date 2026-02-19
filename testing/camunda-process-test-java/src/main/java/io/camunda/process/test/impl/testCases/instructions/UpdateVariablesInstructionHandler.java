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
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.UpdateVariablesInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class UpdateVariablesInstructionHandler
    implements TestCaseInstructionHandler<UpdateVariablesInstruction> {

  @Override
  public void execute(
      final UpdateVariablesInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    if (instruction.getElementSelector().isPresent()) {
      // Local variables: update using element selector
      final ElementSelector elementSelector =
          InstructionSelectorFactory.buildElementSelector(instruction.getElementSelector().get());
      context.updateLocalVariables(
          processInstanceSelector, elementSelector, instruction.getVariables());
    } else {
      // Global variables: update at process instance level
      context.updateVariables(processInstanceSelector, instruction.getVariables());
    }
  }

  @Override
  public Class<UpdateVariablesInstruction> getInstructionType() {
    return UpdateVariablesInstruction.class;
  }
}
