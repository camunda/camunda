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
import io.camunda.process.test.api.testCases.instructions.ImmutableIncreaseTimeInstruction;
import io.camunda.process.test.api.testCases.instructions.IncreaseTimeInstruction;
import io.camunda.process.test.impl.testCases.instructions.IncreaseTimeInstructionHandler;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IncreaseTimeInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final IncreaseTimeInstructionHandler instructionHandler =
      new IncreaseTimeInstructionHandler();

  @Test
  void shouldIncreaseTimeWithDuration() {
    // given
    final Duration duration = Duration.ofDays(2);

    final IncreaseTimeInstruction instruction =
        ImmutableIncreaseTimeInstruction.builder().duration(duration).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).increaseTime(duration);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldIncreaseTimeWithHourDuration() {
    // given
    final Duration duration = Duration.ofHours(1);

    final IncreaseTimeInstruction instruction =
        ImmutableIncreaseTimeInstruction.builder().duration(duration).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).increaseTime(duration);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldIncreaseTimeWithMinuteDuration() {
    // given
    final Duration duration = Duration.ofMinutes(30);

    final IncreaseTimeInstruction instruction =
        ImmutableIncreaseTimeInstruction.builder().duration(duration).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).increaseTime(duration);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
