/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.CorrelateMessageInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCorrelateMessageInstruction;
import io.camunda.process.test.impl.testCases.instructions.CorrelateMessageInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CorrelateMessageInstructionTest {

  private static final String MESSAGE_NAME = "message1";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final CorrelateMessageInstructionHandler instructionHandler =
      new CorrelateMessageInstructionHandler();

  @Test
  void shouldCorrelateMessageWithoutCorrelationKey() {
    // given
    final CorrelateMessageInstruction instruction =
        ImmutableCorrelateMessageInstruction.builder().name(MESSAGE_NAME).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCorrelateMessageCommand();

    final CorrelateMessageCommandStep3 mockCommand =
        camundaClient
            .newCorrelateMessageCommand()
            .messageName(MESSAGE_NAME)
            .withoutCorrelationKey();

    verify(mockCommand).variables(Collections.emptyMap());
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldCorrelateMessageWithCorrelationKey() {
    // given
    final String correlationKey = "order-12345";
    final CorrelateMessageInstruction instruction =
        ImmutableCorrelateMessageInstruction.builder()
            .name(MESSAGE_NAME)
            .correlationKey(correlationKey)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCorrelateMessageCommand();

    final CorrelateMessageCommandStep3 mockCommand =
        camundaClient
            .newCorrelateMessageCommand()
            .messageName(MESSAGE_NAME)
            .correlationKey(correlationKey);

    verify(mockCommand).variables(Collections.emptyMap());
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldSetVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", 12345);
    variables.put("status", "approved");

    final CorrelateMessageInstruction instruction =
        ImmutableCorrelateMessageInstruction.builder()
            .name(MESSAGE_NAME)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCorrelateMessageCommand();

    final CorrelateMessageCommandStep3 mockCommand =
        camundaClient
            .newCorrelateMessageCommand()
            .messageName(MESSAGE_NAME)
            .withoutCorrelationKey();

    verify(mockCommand).variables(variables);
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }
}
