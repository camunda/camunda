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
import io.camunda.process.test.api.testCases.MessageSelector;
import io.camunda.process.test.api.testCases.instructions.AssertProcessInstanceMessageSubscriptionInstruction;
import io.camunda.process.test.api.testCases.instructions.assertProcessInstanceMessageSubscription.MessageSubscriptionState;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class AssertProcessInstanceMessageSubscriptionInstructionHandler
    implements TestCaseInstructionHandler<AssertProcessInstanceMessageSubscriptionInstruction> {

  @Override
  public void execute(
      final AssertProcessInstanceMessageSubscriptionInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    final MessageSelector messageSelector = instruction.getMessageSelector();
    final String messageName = messageSelector.getMessageName();
    final MessageSubscriptionState state = instruction.getState();

    if (messageSelector.getCorrelationKey().isPresent()) {
      final String correlationKey = messageSelector.getCorrelationKey().get();
      assertMessageSubscription(processInstanceAssert, messageName, correlationKey, state);
    } else {
      assertMessageSubscription(processInstanceAssert, messageName, state);
    }
  }

  @Override
  public Class<AssertProcessInstanceMessageSubscriptionInstruction> getInstructionType() {
    return AssertProcessInstanceMessageSubscriptionInstruction.class;
  }

  private static void assertMessageSubscription(
      final ProcessInstanceAssert processInstanceAssert,
      final String messageName,
      final MessageSubscriptionState state) {
    switch (state) {
      case IS_WAITING:
        processInstanceAssert.isWaitingForMessage(messageName);
        break;
      case IS_NOT_WAITING:
        processInstanceAssert.isNotWaitingForMessage(messageName);
        break;
      case IS_CORRELATED:
        processInstanceAssert.hasCorrelatedMessage(messageName);
        break;
      default:
        throw new IllegalArgumentException("Unsupported message subscription state: " + state);
    }
  }

  private static void assertMessageSubscription(
      final ProcessInstanceAssert processInstanceAssert,
      final String messageName,
      final String correlationKey,
      final MessageSubscriptionState state) {
    switch (state) {
      case IS_WAITING:
        processInstanceAssert.isWaitingForMessage(messageName, correlationKey);
        break;
      case IS_NOT_WAITING:
        processInstanceAssert.isNotWaitingForMessage(messageName, correlationKey);
        break;
      case IS_CORRELATED:
        processInstanceAssert.hasCorrelatedMessage(messageName, correlationKey);
        break;
      default:
        throw new IllegalArgumentException("Unsupported message subscription state: " + state);
    }
  }
}
