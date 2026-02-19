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
import io.camunda.process.test.api.testCases.instructions.AssertElementInstancesInstruction;
import io.camunda.process.test.api.testCases.instructions.assertElementInstances.ElementInstancesState;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;
import java.util.List;
import java.util.stream.Collectors;

public class AssertElementInstancesInstructionHandler
    implements TestCaseInstructionHandler<AssertElementInstancesInstruction> {

  @Override
  public void execute(
      final AssertElementInstancesInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final List<ElementSelector> elementSelectors = buildElementSelectors(instruction);

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    final ElementInstancesState state = instruction.getState();

    assertElementsState(processInstanceAssert, elementSelectors, state);
  }

  @Override
  public Class<AssertElementInstancesInstruction> getInstructionType() {
    return AssertElementInstancesInstruction.class;
  }

  private List<ElementSelector> buildElementSelectors(
      final AssertElementInstancesInstruction instruction) {
    return instruction.getElementSelectors().stream()
        .map(InstructionSelectorFactory::buildElementSelector)
        .collect(Collectors.toList());
  }

  private static void assertElementsState(
      final ProcessInstanceAssert processInstanceAssert,
      final List<ElementSelector> elementSelectors,
      final ElementInstancesState state) {
    final ElementSelector[] selectorsArray = elementSelectors.toArray(new ElementSelector[0]);

    switch (state) {
      case IS_ACTIVE:
        processInstanceAssert.hasActiveElements(selectorsArray);
        break;
      case IS_COMPLETED:
        processInstanceAssert.hasCompletedElements(selectorsArray);
        break;
      case IS_TERMINATED:
        processInstanceAssert.hasTerminatedElements(selectorsArray);
        break;
      case IS_NOT_ACTIVE:
        processInstanceAssert.hasNoActiveElements(selectorsArray);
        break;
      case IS_NOT_ACTIVATED:
        processInstanceAssert.hasNotActivatedElements(selectorsArray);
        break;
      case IS_ACTIVE_EXACTLY:
        processInstanceAssert.hasActiveElementsExactly(selectorsArray);
        break;
      case IS_COMPLETED_IN_ORDER:
        processInstanceAssert.hasCompletedElementsInOrder(selectorsArray);
        break;
      default:
        throw new IllegalArgumentException("Unsupported element instance state: " + state);
    }
  }
}
