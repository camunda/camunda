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
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.instructions.ImmutableSetTimeInstruction;
import io.camunda.process.test.api.testCases.instructions.SetTimeInstruction;
import io.camunda.process.test.impl.testCases.instructions.SetTimeInstructionHandler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SetTimeInstructionTest {

  private static final String TIME_STRING = "2025-12-01T10:00:00Z";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final SetTimeInstructionHandler instructionHandler = new SetTimeInstructionHandler();

  @Test
  void shouldSetTime() {
    // given
    final Instant instant = Instant.parse(TIME_STRING);

    final SetTimeInstruction instruction =
        ImmutableSetTimeInstruction.builder().time(instant).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).setTime(instant);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
