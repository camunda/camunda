/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.dsl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteUserTaskJobResultStep1;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.dsl.ImmutableJobSelector;
import io.camunda.process.test.api.dsl.instructions.CompleteJobUserTaskListenerInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCompleteJobUserTaskListenerInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCorrections;
import io.camunda.process.test.impl.dsl.instructions.CompleteJobUserTaskListenerInstructionHandler;
import java.util.Arrays;
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
public class CompleteJobUserTaskListenerInstructionTest {

  private static final String JOB_TYPE = "assign-user";
  private static final String ELEMENT_ID = "task1";
  private static final String PROCESS_DEFINITION_ID = "my-process";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;
  @Mock private JobFilter jobFilter;
  @Captor private ArgumentCaptor<JobSelector> jobSelectorCaptor;

  @Captor private ArgumentCaptor<Consumer<CompleteUserTaskJobResultStep1>> jobResultHandlerCaptor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CompleteUserTaskJobResultStep1 jobResult;

  private final CompleteJobUserTaskListenerInstructionHandler instructionHandler =
      new CompleteJobUserTaskListenerInstructionHandler();

  @Test
  void shouldCompleteJobByJobType() {
    // given
    final CompleteJobUserTaskListenerInstruction instruction =
        ImmutableCompleteJobUserTaskListenerInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfUserTaskListener(
            jobSelectorCaptor.capture(), eq(Collections.emptyMap()), any());

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobByElementId() {
    // given
    final CompleteJobUserTaskListenerInstruction instruction =
        ImmutableCompleteJobUserTaskListenerInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().elementId(ELEMENT_ID).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfUserTaskListener(
            jobSelectorCaptor.capture(), eq(Collections.emptyMap()), any());

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).elementId(ELEMENT_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobByProcessDefinitionId() {
    // given
    final CompleteJobUserTaskListenerInstruction instruction =
        ImmutableCompleteJobUserTaskListenerInstruction.builder()
            .jobSelector(
                ImmutableJobSelector.builder().processDefinitionId(PROCESS_DEFINITION_ID).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfUserTaskListener(
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

    final CompleteJobUserTaskListenerInstruction instruction =
        ImmutableCompleteJobUserTaskListenerInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfUserTaskListener(jobSelectorCaptor.capture(), eq(variables), any());

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithDenied() {
    // given
    final CompleteJobUserTaskListenerInstruction instruction =
        ImmutableCompleteJobUserTaskListenerInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .denied(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfUserTaskListener(
            jobSelectorCaptor.capture(),
            eq(Collections.emptyMap()),
            jobResultHandlerCaptor.capture());

    jobResultHandlerCaptor.getValue().accept(jobResult);

    verify(jobResult).deny(true);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithDeniedReason() {
    // given
    final CompleteJobUserTaskListenerInstruction instruction =
        ImmutableCompleteJobUserTaskListenerInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .denied(true)
            .deniedReason("Task not ready")
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfUserTaskListener(
            jobSelectorCaptor.capture(),
            eq(Collections.emptyMap()),
            jobResultHandlerCaptor.capture());

    jobResultHandlerCaptor.getValue().accept(jobResult);

    verify(jobResult).deny(true);
    verify(jobResult).deniedReason("Task not ready");

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithCorrections() {
    // given
    final CompleteJobUserTaskListenerInstruction instruction =
        ImmutableCompleteJobUserTaskListenerInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().elementId(ELEMENT_ID).build())
            .corrections(
                ImmutableCorrections.builder()
                    .assignee("me")
                    .dueDate("2024-12-31T23:59:59Z")
                    .followUpDate("2024-12-01T00:00:00Z")
                    .addCandidateUsers("user1", "user2")
                    .addCandidateGroups("group1")
                    .priority(100)
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfUserTaskListener(
            jobSelectorCaptor.capture(),
            eq(Collections.emptyMap()),
            jobResultHandlerCaptor.capture());

    jobResultHandlerCaptor.getValue().accept(jobResult);

    verify(jobResult).correctAssignee("me");
    verify(jobResult).correctDueDate("2024-12-31T23:59:59Z");
    verify(jobResult).correctFollowUpDate("2024-12-01T00:00:00Z");
    verify(jobResult).correctCandidateUsers(Arrays.asList("user1", "user2"));
    verify(jobResult).correctCandidateGroups(Collections.singletonList("group1"));
    verify(jobResult).correctPriority(100);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithAllOptions() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("result", "completed");

    final CompleteJobUserTaskListenerInstruction instruction =
        ImmutableCompleteJobUserTaskListenerInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .putAllVariables(variables)
            .denied(false)
            .corrections(
                ImmutableCorrections.builder().assignee("new-assignee").priority(50).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext)
        .completeJobOfUserTaskListener(
            jobSelectorCaptor.capture(), eq(variables), jobResultHandlerCaptor.capture());

    jobResultHandlerCaptor.getValue().accept(jobResult);

    verify(jobResult).correctAssignee("new-assignee");
    verify(jobResult).correctPriority(50);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
