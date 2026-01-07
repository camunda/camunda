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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.dsl.ImmutableJobSelector;
import io.camunda.process.test.api.dsl.instructions.CompleteJobInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCompleteJobInstruction;
import io.camunda.process.test.impl.dsl.instructions.CompleteJobInstructionHandler;
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
public class CompleteJobInstructionTest {

  private static final String JOB_TYPE = "send-notification";
  private static final String ELEMENT_ID = "task1";
  private static final String PROCESS_DEFINITION_ID = "my-process";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;
  @Mock private JobFilter jobFilter;
  @Captor private ArgumentCaptor<JobSelector> jobSelectorCaptor;

  private final CompleteJobInstructionHandler instructionHandler =
      new CompleteJobInstructionHandler();

  @Test
  void shouldCompleteJobByJobType() {
    // given
    final CompleteJobInstruction instruction =
        ImmutableCompleteJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeJob(jobSelectorCaptor.capture(), eq(Map.of()));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobByElementId() {
    // given
    final CompleteJobInstruction instruction =
        ImmutableCompleteJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().elementId(ELEMENT_ID).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeJob(jobSelectorCaptor.capture(), eq(Map.of()));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).elementId(ELEMENT_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobByProcessDefinitionId() {
    // given
    final CompleteJobInstruction instruction =
        ImmutableCompleteJobInstruction.builder()
            .jobSelector(
                ImmutableJobSelector.builder().processDefinitionId(PROCESS_DEFINITION_ID).build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeJob(jobSelectorCaptor.capture(), eq(Map.of()));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).processDefinitionId(PROCESS_DEFINITION_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithCombinedSelector() {
    // given
    final CompleteJobInstruction instruction =
        ImmutableCompleteJobInstruction.builder()
            .jobSelector(
                ImmutableJobSelector.builder()
                    .jobType(JOB_TYPE)
                    .elementId(ELEMENT_ID)
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeJob(jobSelectorCaptor.capture(), eq(Map.of()));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);
    verify(jobFilter).elementId(ELEMENT_ID);
    verify(jobFilter).processDefinitionId(PROCESS_DEFINITION_ID);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 1);
    variables.put("y", "value");

    final CompleteJobInstruction instruction =
        ImmutableCompleteJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeJob(jobSelectorCaptor.capture(), eq(variables));

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldCompleteJobWithExampleData() {
    // given
    final CompleteJobInstruction instruction =
        ImmutableCompleteJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .useExampleData(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).completeJobWithExampleData(jobSelectorCaptor.capture());

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldIgnoreVariablesWhenUseExampleDataIsTrue() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("x", 1);

    final CompleteJobInstruction instruction =
        ImmutableCompleteJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().jobType(JOB_TYPE).build())
            .putAllVariables(variables)
            .useExampleData(true)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    // Should call completeJobWithExampleData, not completeJob with variables
    verify(processTestContext).completeJobWithExampleData(jobSelectorCaptor.capture());

    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(JOB_TYPE);

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldFailIfJobSelectorHasNoProperties() {
    // given
    final CompleteJobInstruction instruction =
        ImmutableCompleteJobInstruction.builder()
            .jobSelector(ImmutableJobSelector.builder().build())
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
