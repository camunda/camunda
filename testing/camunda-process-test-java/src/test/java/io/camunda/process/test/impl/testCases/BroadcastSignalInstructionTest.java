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
import io.camunda.client.api.command.BroadcastSignalCommandStep1.BroadcastSignalCommandStep2;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.BroadcastSignalInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableBroadcastSignalInstruction;
import io.camunda.process.test.impl.testCases.instructions.BroadcastSignalInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BroadcastSignalInstructionTest {

  private static final String SIGNAL_NAME = "signal1";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final BroadcastSignalInstructionHandler instructionHandler =
      new BroadcastSignalInstructionHandler();

  @Test
  void shouldBroadcastSignalWithSignalName() {
    // given
    final BroadcastSignalInstruction instruction =
        ImmutableBroadcastSignalInstruction.builder().signalName(SIGNAL_NAME).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newBroadcastSignalCommand();

    final BroadcastSignalCommandStep2 mockCommand =
        camundaClient
            .newBroadcastSignalCommand()
            .signalName(SIGNAL_NAME)
            .variables(Collections.emptyMap());

    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldSetVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("key1", "value1");
    variables.put("key2", 123);

    final BroadcastSignalInstruction instruction =
        ImmutableBroadcastSignalInstruction.builder()
            .signalName(SIGNAL_NAME)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newBroadcastSignalCommand();

    final BroadcastSignalCommandStep2 mockCommand =
        camundaClient.newBroadcastSignalCommand().signalName(SIGNAL_NAME).variables(variables);

    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }
}
