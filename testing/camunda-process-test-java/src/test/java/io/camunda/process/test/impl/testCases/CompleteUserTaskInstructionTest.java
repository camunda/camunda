/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.testCases.ImmutableUserTaskSelector;
import io.camunda.process.test.api.testCases.instructions.CompleteUserTaskInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableCompleteUserTaskInstruction;
import io.camunda.process.test.impl.testCases.instructions.CompleteUserTaskInstructionHandler;
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
public class CompleteUserTaskInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private UserTaskFilter userTaskFilter;

  @Captor private ArgumentCaptor<UserTaskSelector> selectorCaptor;

  private final CompleteUserTaskInstructionHandler instructionHandler =
      new CompleteUserTaskInstructionHandler();

  @Test
  void shouldCompleteUserTaskByTaskName() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(
                ImmutableUserTaskSelector.builder().taskName("Approve Request").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeUserTask(selectorCaptor.capture(), eq(Collections.emptyMap()));

    selectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).name("Approve Request");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskByElementId() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeUserTask(selectorCaptor.capture(), eq(Collections.emptyMap()));

    selectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).elementId("task1");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskByProcessDefinitionId() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(
                ImmutableUserTaskSelector.builder().processDefinitionId("my-process").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeUserTask(selectorCaptor.capture(), eq(Collections.emptyMap()));

    selectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).bpmnProcessId("my-process");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskWithCombinedSelector() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(
                ImmutableUserTaskSelector.builder()
                    .processDefinitionId("my-process")
                    .elementId("task1")
                    .taskName("Review Task")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeUserTask(selectorCaptor.capture(), eq(Collections.emptyMap()));

    selectorCaptor.getValue().applyFilter(userTaskFilter);
    verify(userTaskFilter).bpmnProcessId("my-process");
    verify(userTaskFilter).elementId("task1");
    verify(userTaskFilter).name("Review Task");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("approved", true);
    variables.put("comment", "Looks good");

    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeUserTask(selectorCaptor.capture(), eq(variables));

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldCompleteUserTaskWithExampleData() {
    // given
    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .useExampleData(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeUserTaskWithExampleData(selectorCaptor.capture());

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldUseExampleDataOverVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 1);
    variables.put("y", 2);

    final CompleteUserTaskInstruction instruction =
        ImmutableCompleteUserTaskInstruction.builder()
            .userTaskSelector(ImmutableUserTaskSelector.builder().elementId("task1").build())
            .putAllVariables(variables)
            .useExampleData(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeUserTaskWithExampleData(selectorCaptor.capture());

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }
}
