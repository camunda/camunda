/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.testCases.ImmutableJobSelector;
import io.camunda.process.test.api.testCases.instructions.CompleteJobAdHocSubProcessInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableActivateElement;
import io.camunda.process.test.api.testCases.instructions.ImmutableCompleteJobAdHocSubProcessInstruction;
import io.camunda.process.test.impl.testCases.instructions.CompleteJobAdHocSubProcessInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompleteJobAdHocSubProcessInstructionTest {

  private static final String JOB_TYPE = "ad-hoc-task";
  private static final String ELEMENT_ID = "ad-hoc-sp";
  private static final String PROCESS_DEFINITION_ID = "my-process";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;
  @Mock private JobFilter jobFilter;
  @Captor private ArgumentCaptor<JobSelector> jobSelectorCaptor;

  @Captor
  private ArgumentCaptor<Consumer<CompleteAdHocSubProcessResultStep1>> jobResultHandlerCaptor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CompleteAdHocSubProcessResultStep1 jobResult;

  private final CompleteJobAdHocSubProcessInstructionHandler instructionHandler =
      new CompleteJobAdHocSubProcessInstructionHandler();

  @Test
  void shouldCompleteJobByJobType() {
    // given
    final CompleteJobAdHocSubProcessInstruction instruction =
        ImmutableCompleteJobAdHocSubProcessInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfAdHocSubProcess(
            jobSelectorCaptor.capture(), eq(Collections.emptyMap()), any());

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobByElementId() {
    // given
    final CompleteJobAdHocSubProcessInstruction instruction =
        ImmutableCompleteJobAdHocSubProcessInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().elementId(ELEMENT_ID).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfAdHocSubProcess(
            jobSelectorCaptor.capture(), eq(Collections.emptyMap()), any());

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).elementId(ELEMENT_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobByProcessDefinitionId() {
    // given
    final CompleteJobAdHocSubProcessInstruction instruction =
        ImmutableCompleteJobAdHocSubProcessInstruction.builder()
            .jobSelector(
                ImmutableJobSelector.builder().processDefinitionId(PROCESS_DEFINITION_ID).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfAdHocSubProcess(
            jobSelectorCaptor.capture(), eq(Collections.emptyMap()), any());

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).processDefinitionId(PROCESS_DEFINITION_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 1);
    variables.put("y", "value");

    final CompleteJobAdHocSubProcessInstruction instruction =
        ImmutableCompleteJobAdHocSubProcessInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfAdHocSubProcess(jobSelectorCaptor.capture(), eq(variables), any());

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithActivateElements() {
    // given
    final CompleteJobAdHocSubProcessInstruction instruction =
        ImmutableCompleteJobAdHocSubProcessInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().elementId(ELEMENT_ID).build())
            .addActivateElements(
                ImmutableActivateElement.builder().elementId("task1").putVariables("x", 1).build())
            .addActivateElements(ImmutableActivateElement.builder().elementId("task2").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfAdHocSubProcess(
            jobSelectorCaptor.capture(),
            eq(Collections.emptyMap()),
            jobResultHandlerCaptor.capture());

    jobResultHandlerCaptor.getValue().accept(jobResult);

    verify(jobResult).activateElement("task1");
    verify(jobResult.activateElement("task1")).variables(Collections.singletonMap("x", 1));
    verify(jobResult).activateElement("task2");
    verify(jobResult.activateElement("task2")).variables(Collections.emptyMap());

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithAllOptions() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("result", "completed");

    final CompleteJobAdHocSubProcessInstruction instruction =
        ImmutableCompleteJobAdHocSubProcessInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .putAllVariables(variables)
            .addActivateElements(
                ImmutableActivateElement.builder().elementId("task1").putVariables("x", 1).build())
            .cancelRemainingInstances(true)
            .completionConditionFulfilled(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfAdHocSubProcess(
            jobSelectorCaptor.capture(), eq(variables), jobResultHandlerCaptor.capture());

    jobResultHandlerCaptor.getValue().accept(jobResult);

    verify(jobResult).activateElement("task1");
    verify(jobResult.activateElement("task1")).variables(Collections.singletonMap("x", 1));
    verify(jobResult).cancelRemainingInstances(true);
    verify(jobResult).completionConditionFulfilled(true);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
