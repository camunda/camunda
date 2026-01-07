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
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.response.Job;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompleteJobTest {

  private static final Long JOB_KEY = 100L;
  private static final String JOB_TYPE = "test-job";
  private static final String ELEMENT_ID = "service-task-1";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaManagementClient camundaManagementClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;

  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private Job job;

  @Captor private ArgumentCaptor<Consumer<JobFilter>> jobFilterCaptor;
  @Mock private JobFilter jobFilter;

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
              camundaManagementClient,
              DevAwaitBehavior.expectSuccess(),
              jsonMapper,
              zeebeJsonMapper);

      when(camundaClient
              .newJobSearchRequest()
              .filter(jobFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(job));

      when(job.getJobKey()).thenReturn(JOB_KEY);
      when(job.getType()).thenReturn(JOB_TYPE);
      when(job.getElementId()).thenReturn(ELEMENT_ID);
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
      verify(jobFilter)
          .state(
              state ->
                  state.in(
                      JobState.CREATED,
                      JobState.FAILED,
                      JobState.RETRIES_UPDATED,
                      JobState.TIMED_OUT));
      verify(jobFilter).retries(retries -> retries.gte(1));

      verifyNoMoreInteractions(jobFilter);
    }

    @Test
    void shouldSearchBySelector() {
      // when
      camundaProcessTestContext.completeJob(JobSelectors.byJobType(JOB_TYPE));

      // then
      jobFilterCaptor.getValue().accept(jobFilter);
      verify(jobFilter).type(JOB_TYPE);
      verify(jobFilter)
          .state(
              state ->
                  state.in(
                      JobState.CREATED,
                      JobState.FAILED,
                      JobState.RETRIES_UPDATED,
                      JobState.TIMED_OUT));
      verify(jobFilter).retries(retries -> retries.gte(1));

      verifyNoMoreInteractions(jobFilter);
    }

    @Test
    void shouldAwaitUntilJobIsPresent() {
      // given
      when(camundaClient.newJobSearchRequest().filter(jobFilterCaptor.capture()).send().join().items())
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
              camundaManagementClient,
              DevAwaitBehavior.expectFailure(),
              jsonMapper,
              zeebeJsonMapper);
    }

    @Test
    void shouldFailIfNoJobIsPresent() {
      // given
      when(camundaClient.newJobSearchRequest().filter(jobFilterCaptor.capture()).send().join().items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(() -> camundaProcessTestContext.completeJob(JOB_TYPE))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete job [jobType: %s] but no job is available.", JOB_TYPE);
    }
  }
}
