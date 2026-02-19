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
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.process.test.api.testCases.instructions.ImmutableMockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.impl.testCases.instructions.MockJobWorkerCompleteJobInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockJobWorkerCompleteJobInstructionTest {

  private static final String JOB_TYPE = "send-email";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private JobWorkerMockBuilder jobWorkerMockBuilder;

  @Mock private JobWorkerMock jobWorkerMock;

  private final MockJobWorkerCompleteJobInstructionHandler instructionHandler =
      new MockJobWorkerCompleteJobInstructionHandler();

  @BeforeEach
  void setUp() {
    when(processTestContext.mockJobWorker(JOB_TYPE)).thenReturn(jobWorkerMockBuilder);
  }

  @Test
  void shouldCompleteJobWithoutVariables() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder().jobType(JOB_TYPE).build();

    when(jobWorkerMockBuilder.thenComplete(Collections.emptyMap())).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenComplete(Collections.emptyMap());

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "sent");
    variables.put("timestamp", 123456789L);

    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .putAllVariables(variables)
            .build();

    when(jobWorkerMockBuilder.thenComplete(variables)).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenComplete(variables);

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldCompleteJobWithExampleData() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .useExampleData(true)
            .build();

    when(jobWorkerMockBuilder.thenCompleteWithExampleData()).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenCompleteWithExampleData();

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldIgnoreVariablesWhenUseExampleDataIsTrue() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "sent");

    final MockJobWorkerCompleteJobInstruction instruction =
        ImmutableMockJobWorkerCompleteJobInstruction.builder()
            .jobType(JOB_TYPE)
            .putAllVariables(variables)
            .useExampleData(true)
            .build();

    when(jobWorkerMockBuilder.thenCompleteWithExampleData()).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenCompleteWithExampleData();

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }
}
