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
import io.camunda.process.test.api.testCases.instructions.ImmutableMockJobWorkerThrowBpmnErrorInstruction;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerThrowBpmnErrorInstruction;
import io.camunda.process.test.impl.testCases.instructions.MockJobWorkerThrowBpmnErrorInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockJobWorkerThrowBpmnErrorInstructionTest {

  private static final String JOB_TYPE = "validate-order";
  private static final String ERROR_CODE = "INVALID_ORDER";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private JobWorkerMockBuilder jobWorkerMockBuilder;

  private final MockJobWorkerThrowBpmnErrorInstructionHandler instructionHandler =
      new MockJobWorkerThrowBpmnErrorInstructionHandler();

  @BeforeEach
  void setUp() {
    when(processTestContext.mockJobWorker(JOB_TYPE)).thenReturn(jobWorkerMockBuilder);
  }

  @Test
  void shouldThrowBpmnErrorWithoutVariablesOrErrorMessage() {
    // given
    final MockJobWorkerThrowBpmnErrorInstruction instruction =
        ImmutableMockJobWorkerThrowBpmnErrorInstruction.builder()
            .jobType(JOB_TYPE)
            .errorCode(ERROR_CODE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenThrowBpmnError(ERROR_CODE, Collections.emptyMap());

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldThrowBpmnErrorWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("reason", "Missing required field");
    variables.put("field", "email");

    final MockJobWorkerThrowBpmnErrorInstruction instruction =
        ImmutableMockJobWorkerThrowBpmnErrorInstruction.builder()
            .jobType(JOB_TYPE)
            .errorCode(ERROR_CODE)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenThrowBpmnError(ERROR_CODE, variables);

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldThrowBpmnErrorWithErrorMessage() {
    // given
    final String errorMessage = "Order validation failed";
    final MockJobWorkerThrowBpmnErrorInstruction instruction =
        ImmutableMockJobWorkerThrowBpmnErrorInstruction.builder()
            .jobType(JOB_TYPE)
            .errorCode(ERROR_CODE)
            .errorMessage(errorMessage)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder)
        .thenThrowBpmnError(ERROR_CODE, errorMessage, Collections.emptyMap());

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldThrowBpmnErrorWithErrorMessageAndVariables() {
    // given
    final String errorMessage = "Order validation failed";
    final Map<String, Object> variables = new HashMap<>();
    variables.put("reason", "Missing required field");
    variables.put("field", "email");

    final MockJobWorkerThrowBpmnErrorInstruction instruction =
        ImmutableMockJobWorkerThrowBpmnErrorInstruction.builder()
            .jobType(JOB_TYPE)
            .errorCode(ERROR_CODE)
            .errorMessage(errorMessage)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenThrowBpmnError(ERROR_CODE, errorMessage, variables);

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }
}
