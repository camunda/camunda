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
import io.camunda.process.test.api.testCases.instructions.AssertElementInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.assertElementInstance.ElementInstanceState;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class AssertElementInstanceInstructionHandler
    implements TestCaseInstructionHandler<AssertElementInstanceInstruction> {

  @Override
  public void execute(
      final AssertElementInstanceInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ElementSelector elementSelector =
        InstructionSelectorFactory.buildElementSelector(instruction.getElementSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    final int amount = instruction.getAmount();
    final ElementInstanceState state = instruction.getState();

    assertElementState(processInstanceAssert, elementSelector, state, amount);
  }

  @Override
  public Class<AssertElementInstanceInstruction> getInstructionType() {
    return AssertElementInstanceInstruction.class;
  }

  private static void assertElementState(
      final ProcessInstanceAssert processInstanceAssert,
      final ElementSelector elementSelector,
      final ElementInstanceState state,
      final int amount) {
    switch (state) {
      case IS_ACTIVE:
        processInstanceAssert.hasActiveElement(elementSelector, amount);
        break;
      case IS_COMPLETED:
        processInstanceAssert.hasCompletedElement(elementSelector, amount);
        break;
      case IS_TERMINATED:
        processInstanceAssert.hasTerminatedElement(elementSelector, amount);
        break;
      default:
        throw new IllegalArgumentException("Unsupported element instance state: " + state);
    }
  }
}
