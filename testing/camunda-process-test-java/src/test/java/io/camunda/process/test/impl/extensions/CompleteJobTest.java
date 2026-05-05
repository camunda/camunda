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
package io.camunda.process.test.impl.extensions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandJobResultStep;
import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.command.CompleteUserTaskJobResultStep1;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.JobStateProperty;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.response.Variable;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompleteJobTest {

  private static final Long JOB_KEY = 100L;
  private static final String JOB_TYPE = "test-job";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;

  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private Job job;

  @Captor private ArgumentCaptor<Consumer<JobFilter>> jobFilterCaptor;
  @Captor private ArgumentCaptor<Consumer<JobStateProperty>> jobStatePropertyCaptor;
  @Captor private ArgumentCaptor<Consumer<IntegerProperty>> retriesPropertyCaptor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private JobFilter jobFilter;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private JobStateProperty jobStateProperty;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private IntegerProperty retriesProperty;

  private CamundaProcessTestContext camundaProcessTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);
  }

  @Nested
  class HappyCases {

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectSuccess,
              jsonMapper,
              new ConditionalBehaviorEngine());

      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(job));

      when(job.getJobKey()).thenReturn(JOB_KEY);
      when(job.getType()).thenReturn(JOB_TYPE);
    }

    @Test
    void shouldCompleteJobByJobType() {
      // when
      camundaProcessTestContext.completeJob(JOB_TYPE);

      // then
      verify(camundaClient.newCompleteCommand(JOB_KEY).variables(Collections.emptyMap())).send();
    }

    @Test
    void shouldCompleteJobWithVariables() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("result", "okay");

      // when
      camundaProcessTestContext.completeJob(JOB_TYPE, variables);

      // then
      verify(camundaClient.newCompleteCommand(JOB_KEY).variables(variables)).send();
    }

    @Test
    void shouldSearchByJobType() {
      // when
      camundaProcessTestContext.completeJob(JOB_TYPE);

      // then
      jobFilterCaptor.getValue().accept(jobFilter);
      verify(jobFilter).type(JOB_TYPE);
      verify(jobFilter).state(jobStatePropertyCaptor.capture());
      verify(jobFilter.state(jobStatePropertyCaptor.getValue()))
          .retries(retriesPropertyCaptor.capture());

      jobStatePropertyCaptor.getValue().accept(jobStateProperty);
      verify(jobStateProperty)
          .in(JobState.CREATED, JobState.FAILED, JobState.RETRIES_UPDATED, JobState.TIMED_OUT);

      retriesPropertyCaptor.getValue().accept(retriesProperty);
      verify(retriesProperty).gte(1);

      verifyNoMoreInteractions(jobFilter);
    }

    @Test
    void shouldSearchBySelector() {
      // when
      camundaProcessTestContext.completeJob(JobSelectors.byJobType(JOB_TYPE));

      // then
      jobFilterCaptor.getValue().accept(jobFilter);
      verify(jobFilter).type(JOB_TYPE);
      verify(jobFilter).state(jobStatePropertyCaptor.capture());
      verify(jobFilter.state(jobStatePropertyCaptor.getValue()))
          .retries(retriesPropertyCaptor.capture());

      jobStatePropertyCaptor.getValue().accept(jobStateProperty);
      verify(jobStateProperty)
          .in(JobState.CREATED, JobState.FAILED, JobState.RETRIES_UPDATED, JobState.TIMED_OUT);

      retriesPropertyCaptor.getValue().accept(retriesProperty);
      verify(retriesProperty).gte(1);

      verifyNoMoreInteractions(jobFilter);
    }

    @Test
    void shouldAwaitUntilJobIsPresent() {
      // given
      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(job));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeJob(JOB_TYPE);

      // then
      verify(camundaClient, times(2)).newJobSearchRequest();
    }

    @Test
    void shouldRetryCompletion() {
      // given
      when(camundaClient
              .newCompleteCommand(JOB_KEY)
              .variables(Collections.emptyMap())
              .send()
              .join())
          .thenThrow(new ClientException("expected"))
          .thenReturn(mock(CompleteJobResponse.class));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeJob(JOB_TYPE);

      // then
      verify(camundaClient, times(2)).newCompleteCommand(JOB_KEY);
    }
  }

  @Nested
  class FailureCases {

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectFailure,
              jsonMapper,
              new ConditionalBehaviorEngine());
    }

    @Test
    void shouldFailIfNoJobIsPresent() {
      // given
      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(() -> camundaProcessTestContext.completeJob(JOB_TYPE))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete job [jobType: %s] but no job is available.", JOB_TYPE);
    }

    @Test
    void shouldFailIfNoAdHocSubProcessJobIsPresent() {
      // given
      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeJobOfAdHocSubProcess(
                      JobSelectors.byJobType(JOB_TYPE), result -> result.activateElement("task1")))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete job [jobKind: AD_HOC_SUB_PROCESS, jobType: %s] but no job is available.",
              JOB_TYPE);
    }

    @Test
    void shouldFailIfNoUserTaskListenerJobIsPresent() {
      // given - override the default mock behavior
      clearInvocations(camundaClient);
      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeJobOfUserTaskListener(
                      JobSelectors.byJobType(JOB_TYPE), result -> {}))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete job [jobKind: TASK_LISTENER, jobType: %s] but no job is available.",
              JOB_TYPE);
    }
  }

  @Nested
  class CompleteJobOfAdHocSubProcess {

    @Mock private CompleteJobCommandJobResultStep completeJobCommandJobResultStep;
    @Mock private CompleteAdHocSubProcessResultStep1 completeAdHocSubProcessResultStep;
    @Mock private Consumer<CompleteAdHocSubProcessResultStep1> jobResultConsumer;

    @Captor
    private ArgumentCaptor<Function<CompleteJobCommandJobResultStep, CompleteJobResult>>
        jobResultFunctionCaptor;

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectSuccess,
              jsonMapper,
              new ConditionalBehaviorEngine());

      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(job));

      when(job.getJobKey()).thenReturn(JOB_KEY);
      when(job.getType()).thenReturn(JOB_TYPE);
      when(job.getKind()).thenReturn(JobKind.AD_HOC_SUB_PROCESS);
    }

    @Test
    void shouldCompleteAdHocSubProcessJobWithoutVariables() {
      // when
      camundaProcessTestContext.completeJobOfAdHocSubProcess(
          JobSelectors.byJobType(JOB_TYPE), result -> result.activateElement("task1"));

      // then
      verify(camundaClient.newCompleteCommand(JOB_KEY).variables(Collections.emptyMap()))
          .withResult(ArgumentMatchers.any());
    }

    @Test
    void shouldCompleteAdHocSubProcessJobWithVariables() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("result", "okay");

      // when
      camundaProcessTestContext.completeJobOfAdHocSubProcess(
          JobSelectors.byJobType(JOB_TYPE), variables, result -> result.activateElement("task1"));

      // then
      verify(camundaClient.newCompleteCommand(JOB_KEY).variables(variables))
          .withResult(ArgumentMatchers.any());
    }

    @Test
    void shouldInvokeJobResultConsumer() {
      // given
      when(completeJobCommandJobResultStep.forAdHocSubProcess())
          .thenReturn(completeAdHocSubProcessResultStep);

      // when
      camundaProcessTestContext.completeJobOfAdHocSubProcess(
          JobSelectors.byJobType(JOB_TYPE), jobResultConsumer);

      // then - verify that withResult was called
      verify(camundaClient.newCompleteCommand(JOB_KEY).variables(Collections.emptyMap()))
          .withResult(jobResultFunctionCaptor.capture());

      // and invoke the captured function to verify our consumer is called
      jobResultFunctionCaptor.getValue().apply(completeJobCommandJobResultStep);
      verify(jobResultConsumer).accept(completeAdHocSubProcessResultStep);
    }

    @Test
    void shouldSearchByJobTypeAndKind() {
      // when
      camundaProcessTestContext.completeJobOfAdHocSubProcess(
          JobSelectors.byJobType(JOB_TYPE), result -> result.activateElement("task1"));

      // then
      jobFilterCaptor.getValue().accept(jobFilter);
      verify(jobFilter).kind(JobKind.AD_HOC_SUB_PROCESS);
      verify(jobFilter).type(JOB_TYPE);
      verify(jobFilter).state(jobStatePropertyCaptor.capture());
      verify(jobFilter.state(jobStatePropertyCaptor.getValue()))
          .retries(retriesPropertyCaptor.capture());

      jobStatePropertyCaptor.getValue().accept(jobStateProperty);
      verify(jobStateProperty)
          .in(JobState.CREATED, JobState.FAILED, JobState.RETRIES_UPDATED, JobState.TIMED_OUT);

      retriesPropertyCaptor.getValue().accept(retriesProperty);
      verify(retriesProperty).gte(1);
    }

    @Test
    void shouldAwaitUntilJobIsPresent() {
      // given
      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(job));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeJobOfAdHocSubProcess(
          JobSelectors.byJobType(JOB_TYPE), result -> result.activateElement("task1"));

      // then
      verify(camundaClient, times(2)).newJobSearchRequest();
    }

    @Test
    void shouldRetryCompletion() {
      // given
      when(camundaClient
              .newCompleteCommand(JOB_KEY)
              .variables(Collections.emptyMap())
              .withResult(ArgumentMatchers.any())
              .send()
              .join())
          .thenThrow(new ClientException("expected"))
          .thenReturn(mock(CompleteJobResponse.class));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeJobOfAdHocSubProcess(
          JobSelectors.byJobType(JOB_TYPE), result -> result.activateElement("task1"));

      // then
      verify(camundaClient, times(2)).newCompleteCommand(JOB_KEY);
    }
  }

  @Nested
  class CompleteJobOfUserTaskListener {

    @Mock private CompleteJobCommandJobResultStep completeJobCommandJobResultStep;

    @Mock private CompleteUserTaskJobResultStep1 completeUserTaskJobResultStep;

    @Mock private Consumer<CompleteUserTaskJobResultStep1> jobResultConsumer;

    @Captor
    private ArgumentCaptor<Function<CompleteJobCommandJobResultStep, CompleteJobResult>>
        jobResultFunctionCaptor;

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectSuccess,
              jsonMapper,
              new ConditionalBehaviorEngine());

      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(job));

      when(job.getJobKey()).thenReturn(JOB_KEY);
      when(job.getType()).thenReturn(JOB_TYPE);
      when(job.getKind()).thenReturn(JobKind.TASK_LISTENER);
    }

    @Test
    void shouldCompleteUserTaskListenerJob() {
      // when
      camundaProcessTestContext.completeJobOfUserTaskListener(
          JobSelectors.byJobType(JOB_TYPE), result -> {});

      // then
      verify(camundaClient.newCompleteCommand(JOB_KEY)).withResult(ArgumentMatchers.any());
    }

    @Test
    void shouldInvokeJobResultConsumer() {
      // given
      when(completeJobCommandJobResultStep.forUserTask()).thenReturn(completeUserTaskJobResultStep);

      // when
      camundaProcessTestContext.completeJobOfUserTaskListener(
          JobSelectors.byJobType(JOB_TYPE), jobResultConsumer);

      // then - verify that withResult was called
      verify(camundaClient.newCompleteCommand(JOB_KEY))
          .withResult(jobResultFunctionCaptor.capture());

      // and invoke the captured function to verify our consumer is called
      jobResultFunctionCaptor.getValue().apply(completeJobCommandJobResultStep);
      verify(jobResultConsumer).accept(completeUserTaskJobResultStep);
    }

    @Test
    void shouldSearchByJobTypeAndKind() {
      // when
      camundaProcessTestContext.completeJobOfUserTaskListener(
          JobSelectors.byJobType(JOB_TYPE), result -> {});

      // then
      jobFilterCaptor.getValue().accept(jobFilter);
      verify(jobFilter).kind(JobKind.TASK_LISTENER);
      verify(jobFilter).type(JOB_TYPE);
      verify(jobFilter).state(jobStatePropertyCaptor.capture());
      verify(jobFilter.state(jobStatePropertyCaptor.getValue()))
          .retries(retriesPropertyCaptor.capture());

      jobStatePropertyCaptor.getValue().accept(jobStateProperty);
      verify(jobStateProperty)
          .in(JobState.CREATED, JobState.FAILED, JobState.RETRIES_UPDATED, JobState.TIMED_OUT);

      retriesPropertyCaptor.getValue().accept(retriesProperty);
      verify(retriesProperty).gte(1);
    }

    @Test
    void shouldAwaitUntilJobIsPresent() {
      // given
      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(job));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeJobOfUserTaskListener(
          JobSelectors.byJobType(JOB_TYPE), result -> {});

      // then
      verify(camundaClient, times(2)).newJobSearchRequest();
    }

    @Test
    void shouldRetryCompletion() {
      // given
      when(camundaClient
              .newCompleteCommand(JOB_KEY)
              .withResult(ArgumentMatchers.any())
              .send()
              .join())
          .thenThrow(new ClientException("expected"))
          .thenReturn(mock(CompleteJobResponse.class));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeJobOfUserTaskListener(
          JobSelectors.byJobType(JOB_TYPE), result -> {});

      // then
      verify(camundaClient, times(2)).newCompleteCommand(JOB_KEY);
    }
  }

  @Nested
  @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
  class CompleteJobWithVariableMapper {

    private static final long PROCESS_INSTANCE_KEY = 200L;
    private static final long ELEMENT_INSTANCE_KEY = 300L;

    @Captor private ArgumentCaptor<Consumer<VariableFilter>> variableFilterCaptor;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private VariableFilter variableFilter;

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectSuccess,
              jsonMapper,
              new ConditionalBehaviorEngine());

      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(job));

      when(job.getJobKey()).thenReturn(JOB_KEY);
      when(job.getType()).thenReturn(JOB_TYPE);
      when(job.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
      when(job.getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);

      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());
    }

    private Variable variable(
        final String name, final String value, final long scopeKey, final boolean truncated) {
      final Variable variable = mock(Variable.class);
      when(variable.getName()).thenReturn(name);
      when(variable.getValue()).thenReturn(value);
      when(variable.getScopeKey()).thenReturn(scopeKey);
      when(variable.isTruncated()).thenReturn(truncated);
      return variable;
    }

    @Test
    void shouldCompleteJobWithVariableMapper() {
      // given
      final Variable idVariable = variable("id", "1", PROCESS_INSTANCE_KEY, false);
      final Variable localVariable = variable("local", "\"hello\"", ELEMENT_INSTANCE_KEY, false);
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Arrays.asList(idVariable, localVariable));
      when(jsonMapper.fromJson("1", Object.class)).thenReturn(1);
      when(jsonMapper.fromJson("\"hello\"", Object.class)).thenReturn("hello");

      final Map<String, Object> capturedInput = new HashMap<>();
      final Map<String, Object> outputVariables = Collections.singletonMap("user", "Alice");

      // when
      camundaProcessTestContext.completeJob(
          JOB_TYPE,
          inputVars -> {
            capturedInput.putAll(inputVars);
            return outputVariables;
          });

      // then
      org.assertj.core.api.Assertions.assertThat(capturedInput)
          .containsEntry("id", 1)
          .containsEntry("local", "hello");
      verify(camundaClient.newCompleteCommand(JOB_KEY).variables(outputVariables)).send();
    }

    @Test
    void shouldCompleteJobWithVariableMapperBySelector() {
      // given
      final Map<String, Object> outputVariables = Collections.singletonMap("user", "Bob");

      // when
      camundaProcessTestContext.completeJob(
          JobSelectors.byJobType(JOB_TYPE), inputVars -> outputVariables);

      // then
      verify(camundaClient.newCompleteCommand(JOB_KEY).variables(outputVariables)).send();
    }

    @Test
    void shouldCompleteJobWithEmptyInputVariables() {
      // given - the default mock returns an empty list of variables
      final Map<String, Object> capturedInput = new HashMap<>();
      capturedInput.put("sentinel", "untouched");

      // when
      camundaProcessTestContext.completeJob(
          JOB_TYPE,
          inputVars -> {
            capturedInput.clear();
            capturedInput.putAll(inputVars);
            return Collections.emptyMap();
          });

      // then
      org.assertj.core.api.Assertions.assertThat(capturedInput).isEmpty();
      verify(camundaClient.newCompleteCommand(JOB_KEY).variables(Collections.emptyMap())).send();
    }

    @Test
    void shouldPreferLocalOverGlobalVariable() {
      // given - same name 'id' at both scopes; local must win
      final Variable globalId = variable("id", "1", PROCESS_INSTANCE_KEY, false);
      final Variable localId = variable("id", "2", ELEMENT_INSTANCE_KEY, false);
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Arrays.asList(globalId, localId));
      when(jsonMapper.fromJson("2", Object.class)).thenReturn(2);

      final Map<String, Object> capturedInput = new HashMap<>();

      // when
      camundaProcessTestContext.completeJob(
          JOB_TYPE,
          inputVars -> {
            capturedInput.putAll(inputVars);
            return Collections.emptyMap();
          });

      // then
      org.assertj.core.api.Assertions.assertThat(capturedInput).containsEntry("id", 2);
    }

    @Test
    void shouldFilterVariablesByProcessInstanceKey() {
      // when
      camundaProcessTestContext.completeJob(JOB_TYPE, inputVars -> Collections.emptyMap());

      // then
      variableFilterCaptor.getValue().accept(variableFilter);
      verify(variableFilter).processInstanceKey(PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfVariableMapperIsNull() {
      // given
      clearInvocations(camundaClient);

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeJob(
                      JOB_TYPE, (Function<Map<String, Object>, Map<String, Object>>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("variableMapper");

      // and: no search/completion took place
      verify(camundaClient, org.mockito.Mockito.never()).newJobSearchRequest();
    }

    @Test
    void shouldRetryCompletionWithoutReinvokingMapper() {
      // given
      when(camundaClient
              .newCompleteCommand(JOB_KEY)
              .variables(ArgumentMatchers.<Map<String, Object>>any())
              .send()
              .join())
          .thenThrow(new ClientException("expected"))
          .thenReturn(mock(CompleteJobResponse.class));

      final java.util.concurrent.atomic.AtomicInteger mapperInvocations =
          new java.util.concurrent.atomic.AtomicInteger();

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeJob(
          JOB_TYPE,
          inputVars -> {
            mapperInvocations.incrementAndGet();
            return Collections.emptyMap();
          });

      // then
      org.assertj.core.api.Assertions.assertThat(mapperInvocations.get()).isEqualTo(1);
      verify(camundaClient, times(2)).newCompleteCommand(JOB_KEY);
    }
  }

  @Nested
  @org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
  class CompleteJobWithVariableMapperFailureCases {

    private static final long PROCESS_INSTANCE_KEY = 200L;
    private static final long ELEMENT_INSTANCE_KEY = 300L;

    @Captor private ArgumentCaptor<Consumer<VariableFilter>> variableFilterCaptor;

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              clockClient,
              DevAwaitBehavior::expectFailure,
              jsonMapper,
              new ConditionalBehaviorEngine());
    }

    private Variable variable(
        final String name, final String value, final long scopeKey, final boolean truncated) {
      final Variable variable = mock(Variable.class);
      when(variable.getName()).thenReturn(name);
      when(variable.getValue()).thenReturn(value);
      when(variable.getScopeKey()).thenReturn(scopeKey);
      when(variable.isTruncated()).thenReturn(truncated);
      return variable;
    }

    private void mockJobFound() {
      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(job));
      when(job.getJobKey()).thenReturn(JOB_KEY);
      when(job.getType()).thenReturn(JOB_TYPE);
      when(job.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
      when(job.getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);
    }

    @Test
    void shouldFailIfNoJobIsPresentForVariableMapper() {
      // given
      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      final java.util.concurrent.atomic.AtomicInteger mapperInvocations =
          new java.util.concurrent.atomic.AtomicInteger();

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeJob(
                      JOB_TYPE,
                      inputVars -> {
                        mapperInvocations.incrementAndGet();
                        return Collections.emptyMap();
                      }))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete job [jobType: %s] but no job is available.", JOB_TYPE);

      // and: mapper was never invoked
      org.assertj.core.api.Assertions.assertThat(mapperInvocations.get()).isZero();
    }

    @Test
    void shouldFailIfVariableMapperReturnsNull() {
      // given
      mockJobFound();
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(() -> camundaProcessTestContext.completeJob(JOB_TYPE, inputVars -> null))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete job [jobType: %s] but the variableMapper returned null.",
              JOB_TYPE);

      // and: completion command was never sent
      verify(camundaClient, org.mockito.Mockito.never()).newCompleteCommand(JOB_KEY);
    }

    @Test
    void shouldPropagateExceptionFromVariableMapper() {
      // given
      mockJobFound();
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeJob(
                      JOB_TYPE,
                      inputVars -> {
                        throw new IllegalStateException("boom");
                      }))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("boom");

      // and: completion command was never sent
      verify(camundaClient, org.mockito.Mockito.never()).newCompleteCommand(JOB_KEY);
    }

    @Test
    void shouldFailIfInputVariableIsTruncated() {
      // given
      mockJobFound();
      final Variable truncated = variable("big", "\"...\"", PROCESS_INSTANCE_KEY, true);
      when(camundaClient
              .newVariableSearchRequest()
              .filter(variableFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(truncated));

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.completeJob(
                      JOB_TYPE, inputVars -> Collections.emptyMap()))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("variable 'big' is truncated");

      // and: completion command was never sent
      verify(camundaClient, org.mockito.Mockito.never()).newCompleteCommand(JOB_KEY);
    }
  }
}
