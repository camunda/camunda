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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
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

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaClockClient clockClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;
  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private ActivatedJob activatedJob;

  @Captor private ArgumentCaptor<JobHandler> handlerCaptor;

  private CamundaProcessTestContext processTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);

    processTestContext =
        new CamundaProcessTestContextImpl(
            camundaProcessTestRuntime,
            clientCreationCallback,
            clockClient,
            DevAwaitBehavior::expectSuccess,
            jsonMapper,
            zeebeJsonMapper,
            new ConditionalBehaviorEngine());
  }

  @Test
  void shouldHaveNoInvocationsInitially() throws Exception {
    // when: creating a mock worker via the context API
    final JobWorkerMock jobWorkerMock = processTestContext.mockJobWorker(JOB_TYPE).thenComplete();

    // then: the mock has no invocations and no activated jobs
    assertThat(jobWorkerMock.getInvocations()).isZero();
    assertThat(jobWorkerMock.getActivatedJobs()).isEmpty();

    // and: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    // and: invoking the handler completes the job with no variables
    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(camundaClient.newCompleteCommand(activatedJob).variables(new HashMap<>())).send();
  }

  @Test
  void shouldOpenJobWorkerWithThenCompleteWithVariables() throws Exception {
    // given
    final Map<String, Object> variables = Collections.singletonMap("result", "ok");

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variables);

    // then: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    // and: invoking the handler completes the job with the given variables
    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(camundaClient.newCompleteCommand(activatedJob).variables(variables)).send();
  }

  @Test
  void shouldOpenJobWorkerWithThenThrowBpmnError() throws Exception {
    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenThrowBpmnError("error-code");

    // then: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    // and: invoking the handler throws the BPMN error with no variables
    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newThrowErrorCommand(activatedJob)
                .errorCode("error-code")
                .variables(new HashMap<>()))
        .send();
  }

  @Test
  void shouldOpenJobWorkerWithThenThrowBpmnErrorWithVariables() throws Exception {
    // given
    final Map<String, Object> variables = Collections.singletonMap("error", true);

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenThrowBpmnError("error-code", variables);

    // then: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    // and: invoking the handler throws the BPMN error with the given variables
    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newThrowErrorCommand(activatedJob)
                .errorCode("error-code")
                .variables(variables))
        .send();
  }

  @Test
  void shouldOpenJobWorkerWithThenThrowBpmnErrorWithMessageAndVariables() throws Exception {
    // given
    final Map<String, Object> variables = Collections.singletonMap("error", true);

    // when
    processTestContext
        .mockJobWorker(JOB_TYPE)
        .thenThrowBpmnError("error-code", "error message", variables);

    // then: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    // and: invoking the handler throws the BPMN error with the message and variables
    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    final ThrowErrorCommandStep2 throwCommand =
        camundaClient
            .newThrowErrorCommand(activatedJob)
            .errorCode("error-code")
            .variables(variables);
    verify(throwCommand).errorMessage("error message");
    verify(throwCommand).send();
  }

  @Test
  void shouldOpenJobWorkerWithCustomHandler() {
    // when
    processTestContext.mockJobWorker(JOB_TYPE).withHandler((client, job) -> {});

    // then: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();
  }

  @Test
  void shouldFailJobWhenHandlerThrowsAssertionError() throws Exception {
    // given: a handler that throws an AssertionError
    when(activatedJob.getKey()).thenReturn(JOB_KEY);
    final JobHandler failingHandler =
        (client, job) -> {
          throw new AssertionError("test assertion error in MockJobWorker");
        };

    // create the mock via the context API (this registers the safe handler wrapping the failing
    // handler)
    processTestContext.mockJobWorker(JOB_TYPE).withHandler(failingHandler);

    // capture the safe handler that was registered with the client
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    final JobHandler safeHandler = handlerCaptor.getValue();

    // when: the safe handler processes a job with a failing user handler
    safeHandler.handle(camundaClient, activatedJob);

    // then: the AssertionError is caught and the job is failed with 0 retries
    verify(camundaClient.newFailCommand(JOB_KEY).retries(0)).send();
  }
}
