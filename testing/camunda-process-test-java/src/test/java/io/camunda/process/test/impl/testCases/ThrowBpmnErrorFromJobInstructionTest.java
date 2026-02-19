/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.testCases.ImmutableJobSelector;
import io.camunda.process.test.api.testCases.instructions.ImmutableThrowBpmnErrorFromJobInstruction;
import io.camunda.process.test.api.testCases.instructions.ThrowBpmnErrorFromJobInstruction;
import io.camunda.process.test.impl.testCases.instructions.ThrowBpmnErrorFromJobInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ThrowBpmnErrorFromJobInstructionTest {

  private static final String JOB_TYPE = "send-notification";
  private static final String ELEMENT_ID = "task1";
  private static final String PROCESS_DEFINITION_ID = "my-process";
  private static final String ERROR_CODE = "VALIDATION_FAILED";
  private static final String ERROR_MESSAGE = "Validation error occurred";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;
  @Mock private JobFilter jobFilter;
  @Captor private ArgumentCaptor<JobSelector> jobSelectorCaptor;
  @Captor private ArgumentCaptor<String> errorCodeCaptor;
  @Captor private ArgumentCaptor<String> errorMessageCaptor;

  private final ThrowBpmnErrorFromJobInstructionHandler instructionHandler =
      new ThrowBpmnErrorFromJobInstructionHandler();

  @Test
  void shouldThrowBpmnErrorByJobType() {
    // given
    final ThrowBpmnErrorFromJobInstruction instruction =
        ImmutableThrowBpmnErrorFromJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .errorCode(ERROR_CODE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .throwBpmnErrorFromJob(
            jobSelectorCaptor.capture(), eq(ERROR_CODE), eq(Collections.emptyMap()));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldThrowBpmnErrorByElementId() {
    // given
    final ThrowBpmnErrorFromJobInstruction instruction =
        ImmutableThrowBpmnErrorFromJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().elementId(ELEMENT_ID).build())
            .errorCode(ERROR_CODE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .throwBpmnErrorFromJob(
            jobSelectorCaptor.capture(), eq(ERROR_CODE), eq(Collections.emptyMap()));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).elementId(ELEMENT_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldThrowBpmnErrorByProcessDefinitionId() {
    // given
    final ThrowBpmnErrorFromJobInstruction instruction =
        ImmutableThrowBpmnErrorFromJobInstruction.builder()
            .jobSelector(
                ImmutableJobSelector.builder().processDefinitionId(PROCESS_DEFINITION_ID).build())
            .errorCode(ERROR_CODE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .throwBpmnErrorFromJob(
            jobSelectorCaptor.capture(), eq(ERROR_CODE), eq(Collections.emptyMap()));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).processDefinitionId(PROCESS_DEFINITION_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldThrowBpmnErrorWithCombinedSelector() {
    // given
    final ThrowBpmnErrorFromJobInstruction instruction =
        ImmutableThrowBpmnErrorFromJobInstruction.builder()
            .jobSelector(
                ImmutableJobSelector.builder()
                    .jobType(JOB_TYPE)
                    .elementId(ELEMENT_ID)
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .errorCode(ERROR_CODE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .throwBpmnErrorFromJob(
            jobSelectorCaptor.capture(), eq(ERROR_CODE), eq(Collections.emptyMap()));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);
    verify(jobFilter).elementId(ELEMENT_ID);
    verify(jobFilter).processDefinitionId(PROCESS_DEFINITION_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldThrowBpmnErrorWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 1);
    variables.put("y", "value");

    final ThrowBpmnErrorFromJobInstruction instruction =
        ImmutableThrowBpmnErrorFromJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .errorCode(ERROR_CODE)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .throwBpmnErrorFromJob(jobSelectorCaptor.capture(), eq(ERROR_CODE), eq(variables));

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldThrowBpmnErrorWithErrorMessage() {
    // given
    final ThrowBpmnErrorFromJobInstruction instruction =
        ImmutableThrowBpmnErrorFromJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .errorCode(ERROR_CODE)
            .errorMessage(ERROR_MESSAGE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .throwBpmnErrorFromJob(
            jobSelectorCaptor.capture(),
            eq(ERROR_CODE),
            eq(ERROR_MESSAGE),
            eq(Collections.emptyMap()));

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldThrowBpmnErrorWithErrorMessageAndVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 1);

    final ThrowBpmnErrorFromJobInstruction instruction =
        ImmutableThrowBpmnErrorFromJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .errorCode(ERROR_CODE)
            .errorMessage(ERROR_MESSAGE)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .throwBpmnErrorFromJob(
            jobSelectorCaptor.capture(), eq(ERROR_CODE), eq(ERROR_MESSAGE), eq(variables));

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldFailIfJobSelectorHasNoProperties() {
    // given
    final ThrowBpmnErrorFromJobInstruction instruction =
        ImmutableThrowBpmnErrorFromJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().build())
            .errorCode(ERROR_CODE)
            .build();

    // when/then
    assertThatThrownBy(
            () ->
                instructionHandler.execute(
                    instruction, processTestContext, camundaClient, assertionFacade))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Missing required property: at least one of jobType, elementId, or processDefinitionId must be set");
  }
}
