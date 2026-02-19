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
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.AssertVariablesInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.util.Optional;

public class AssertVariablesInstructionHandler
    implements TestCaseInstructionHandler<AssertVariablesInstruction> {

  @Override
  public void execute(
      final AssertVariablesInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    final Optional<ElementSelector> elementSelector =
        instruction.getElementSelector().map(InstructionSelectorFactory::buildElementSelector);

    // Assert variable names
    if (!instruction.getVariableNames().isEmpty()) {
      if (elementSelector.isPresent()) {
        processInstanceAssert.hasLocalVariableNames(
            elementSelector.get(), instruction.getVariableNames().toArray(new String[0]));
      } else {
        processInstanceAssert.hasVariableNames(
            instruction.getVariableNames().toArray(new String[0]));
      }
    }

    // Assert variables with values
    if (!instruction.getVariables().isEmpty()) {
      if (elementSelector.isPresent()) {
        processInstanceAssert.hasLocalVariables(elementSelector.get(), instruction.getVariables());
      } else {
        processInstanceAssert.hasVariables(instruction.getVariables());
      }
    }
  }

  @Override
  public Class<AssertVariablesInstruction> getInstructionType() {
    return AssertVariablesInstruction.class;
  }
}
