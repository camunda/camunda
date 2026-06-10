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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.process.test.impl.mock.JobWorkerMockImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockJobWorkerTest {

  private static final String JOB_TYPE = "test-job";
  private static final Long JOB_KEY = 42L;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private ActivatedJob activatedJob;

  @Captor private ArgumentCaptor<JobHandler> handlerCaptor;

  @BeforeEach
  void configureMocks() {
    when(activatedJob.getKey()).thenReturn(JOB_KEY);
  }

  @Test
  void shouldHaveNoInvocationsInitially() {
    // when: creating a mock worker
    final JobWorkerMock jobWorkerMock =
        new JobWorkerMockImpl(JOB_TYPE, camundaClient, (client, job) -> {});

    // then: the mock has no invocations and no activated jobs
    assertThat(jobWorkerMock.getInvocations()).isZero();
    assertThat(jobWorkerMock.getActivatedJobs()).isEmpty();
  }

  @Test
  void shouldFailJobWhenHandlerThrowsAssertionError() throws Exception {
    // given: a handler that throws an AssertionError
    final JobHandler failingHandler =
        (client, job) -> {
          throw new AssertionError("test assertion error in MockJobWorker");
        };

    // Create the mock (this registers the safe handler wrapping the failing handler)
    new JobWorkerMockImpl(JOB_TYPE, camundaClient, failingHandler);

    // Capture the safe handler that was registered
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    final JobHandler safeHandler = handlerCaptor.getValue();

    // when: the safe handler processes a job with a failing user handler
    safeHandler.handle(camundaClient, activatedJob);

    // then: the AssertionError is caught and the job is failed with 0 retries
    verify(camundaClient.newFailCommand(JOB_KEY).retries(0)).send();
  }
}
