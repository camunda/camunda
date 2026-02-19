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
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.testCases.instructions.AssertProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.assertProcessInstance.ProcessInstanceState;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class AssertProcessInstanceInstructionHandler
    implements TestCaseInstructionHandler<AssertProcessInstanceInstruction> {

  @Override
  public void execute(
      final AssertProcessInstanceInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    instruction
        .getState()
        .ifPresent(expectedState -> assertState(processInstanceAssert, expectedState));

    instruction
        .hasActiveIncidents()
        .ifPresent(
            hasActiveIncidents -> assertActiveIncidents(processInstanceAssert, hasActiveIncidents));
  }

  @Override
  public Class<AssertProcessInstanceInstruction> getInstructionType() {
    return AssertProcessInstanceInstruction.class;
  }

  private static void assertState(
      final ProcessInstanceAssert processInstanceAssert, final ProcessInstanceState expectedState) {
    switch (expectedState) {
      case IS_ACTIVE:
        processInstanceAssert.isActive();
        break;
      case IS_COMPLETED:
        processInstanceAssert.isCompleted();
        break;
      case IS_TERMINATED:
        processInstanceAssert.isTerminated();
        break;
      case IS_CREATED:
        processInstanceAssert.isCreated();
        break;
      default:
        throw new IllegalArgumentException("Unsupported process instance state: " + expectedState);
    }
  }

  private static void assertActiveIncidents(
      final ProcessInstanceAssert processInstanceAssert, final Boolean hasActiveIncidents) {
    if (hasActiveIncidents) {
      processInstanceAssert.hasActiveIncidents();
    } else {
      processInstanceAssert.hasNoActiveIncidents();
    }
  }
}
