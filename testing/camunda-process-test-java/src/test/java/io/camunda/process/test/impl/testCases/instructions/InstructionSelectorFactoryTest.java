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
package io.camunda.process.test.impl.testCases.instructions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.testCases.ImmutableIncidentSelector;
import io.camunda.process.test.api.testCases.ImmutableJobSelector;
import io.camunda.process.test.api.testCases.ImmutableUserTaskSelector;
import io.camunda.process.test.impl.testCases.TestRunContext;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InstructionSelectorFactoryTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String JOB_TYPE = "job-type";
  private static final String RUN_ID = "run-1";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private JobFilter jobFilter;

  private void stubInstanceSearch(final Long... keys) {
    final List<ProcessInstance> instances =
        Arrays.stream(keys)
            .map(
                key -> {
                  final ProcessInstance pi = mock(ProcessInstance.class);
                  when(pi.getProcessInstanceKey()).thenReturn(key);
                  return pi;
                })
            .collect(Collectors.toList());
    when(camundaClient
            .newProcessInstanceSearchRequest()
            .filter(any(Consumer.class))
            .send()
            .join()
            .items())
        .thenReturn(instances);
  }

  @Test
  void shouldNotScopeJobWhenDisabled() {
    // given
    final io.camunda.process.test.api.testCases.JobSelector dsl =
        ImmutableJobSelector.builder()
            .jobType(JOB_TYPE)
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .build();

    // when
    final JobSelector selector =
        InstructionSelectorFactory.buildJobSelector(dsl, TestRunContext.disabled(), camundaClient);
    selector.applyFilter(jobFilter);

    // then: no instance lookup and no run-scope key applied
    verifyNoInteractions(camundaClient);
    verify(jobFilter, never()).processInstanceKey(anyLong());
  }

  @Test
  void shouldScopeJobWithoutProcessDefinition() {
    // given: job type only — the run's instances are resolved by the run-ID variable, not the def
    stubInstanceSearch(123L);
    final io.camunda.process.test.api.testCases.JobSelector dsl =
        ImmutableJobSelector.builder().jobType(JOB_TYPE).build();

    // when
    final JobSelector selector =
        InstructionSelectorFactory.buildJobSelector(
            dsl, TestRunContext.enabled(RUN_ID), camundaClient);
    selector.applyFilter(jobFilter);

    // then
    verify(jobFilter).processInstanceKey(123L);
  }

  @Test
  void shouldScopeJobToSingleRunInstanceKey() {
    // given
    stubInstanceSearch(123L);
    final io.camunda.process.test.api.testCases.JobSelector dsl =
        ImmutableJobSelector.builder()
            .jobType(JOB_TYPE)
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .build();

    // when
    final JobSelector selector =
        InstructionSelectorFactory.buildJobSelector(
            dsl, TestRunContext.enabled(RUN_ID), camundaClient);
    selector.applyFilter(jobFilter);

    // then
    verify(jobFilter).processInstanceKey(123L);
  }

  @Test
  void shouldScopeJobToNoMatchWhenRunInstanceNotVisibleYet() {
    // given: the run's instance is not exported yet
    stubInstanceSearch();
    final io.camunda.process.test.api.testCases.JobSelector dsl =
        ImmutableJobSelector.builder()
            .jobType(JOB_TYPE)
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .build();

    // when
    final JobSelector selector =
        InstructionSelectorFactory.buildJobSelector(
            dsl, TestRunContext.enabled(RUN_ID), camundaClient);
    selector.applyFilter(jobFilter);

    // then: match nothing so the surrounding await keeps retrying
    verify(jobFilter).processInstanceKey(-1L);
  }

  @Test
  void shouldScopeJobToSeveralRunInstanceKeys() {
    // given
    stubInstanceSearch(1L, 2L);
    final io.camunda.process.test.api.testCases.JobSelector dsl =
        ImmutableJobSelector.builder()
            .jobType(JOB_TYPE)
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .build();

    // when
    final JobSelector selector =
        InstructionSelectorFactory.buildJobSelector(
            dsl, TestRunContext.enabled(RUN_ID), camundaClient);
    selector.applyFilter(jobFilter);

    // then: an IN filter over the keys, not a single key
    verify(jobFilter).processInstanceKey(any(Consumer.class));
    verify(jobFilter, never()).processInstanceKey(anyLong());
  }

  @Test
  void shouldScopeUserTaskToRunInstanceKey() {
    // given
    stubInstanceSearch(123L);
    final UserTaskFilter userTaskFilter = mock(UserTaskFilter.class);
    final io.camunda.process.test.api.testCases.UserTaskSelector dsl =
        ImmutableUserTaskSelector.builder().processDefinitionId(PROCESS_DEFINITION_ID).build();

    // when
    final UserTaskSelector selector =
        InstructionSelectorFactory.buildUserTaskSelector(
            dsl, TestRunContext.enabled(RUN_ID), camundaClient);
    selector.applyFilter(userTaskFilter);

    // then
    verify(userTaskFilter).processInstanceKey(123L);
  }

  @Test
  void shouldScopeIncidentToRunInstanceKey() {
    // given
    stubInstanceSearch(123L);
    final IncidentFilter incidentFilter = mock(IncidentFilter.class);
    final io.camunda.process.test.api.testCases.IncidentSelector dsl =
        ImmutableIncidentSelector.builder().processDefinitionId(PROCESS_DEFINITION_ID).build();

    // when
    final IncidentSelector selector =
        InstructionSelectorFactory.buildIncidentSelector(
            dsl, TestRunContext.enabled(RUN_ID), camundaClient);
    selector.applyFilter(incidentFilter);

    // then
    verify(incidentFilter).processInstanceKey(123L);
  }
}
