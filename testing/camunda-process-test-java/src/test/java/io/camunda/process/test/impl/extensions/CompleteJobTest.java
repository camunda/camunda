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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.JobStateProperty;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.client.CamundaClockClient;
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

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;

  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private ActivatedJob job;

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
              DevAwaitBehavior.expectSuccess(),
              jsonMapper,
              zeebeJsonMapper);

      when(camundaClient
              .newActivateJobsCommand()
              .jobType(JOB_TYPE)
              .maxJobsToActivate(anyInt())
              .requestTimeout(any())
              .send()
              .join()
              .getJobs())
          .thenReturn(Collections.singletonList(job));

      when(job.getKey()).thenReturn(JOB_KEY);
    }

    @Test
    void shouldCompleteJobByJobType() {
      // when
      camundaProcessTestContext.completeJob(JOB_TYPE);

      // then
      verify(camundaClient.newCompleteCommand(job).variables(Collections.emptyMap())).send();
    }

    @Test
    void shouldCompleteJobWithVariables() {
      // given
      final Map<String, Object> variables = Collections.singletonMap("result", "okay");

      // when
      camundaProcessTestContext.completeJob(JOB_TYPE, variables);

      // then
      verify(camundaClient.newCompleteCommand(job).variables(variables)).send();
    }

    @Test
    void shouldAwaitUntilJobIsPresent() {
      // given
      when(camundaClient
              .newActivateJobsCommand()
              .jobType(JOB_TYPE)
              .maxJobsToActivate(anyInt())
              .requestTimeout(any())
              .send()
              .join()
              .getJobs())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(job));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.completeJob(JOB_TYPE);

      // then
      verify(camundaClient, times(2)).newActivateJobsCommand();
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
              DevAwaitBehavior.expectFailure(),
              jsonMapper,
              zeebeJsonMapper);
    }

    @Test
    void shouldFailIfNoJobIsPresent() {
      // given
      when(camundaClient
              .newActivateJobsCommand()
              .jobType(JOB_TYPE)
              .maxJobsToActivate(anyInt())
              .requestTimeout(any())
              .send()
              .join()
              .getJobs())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(() -> camundaProcessTestContext.completeJob(JOB_TYPE))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to complete a job with the type '%s' but no job is available.", JOB_TYPE);
    }
  }
}
