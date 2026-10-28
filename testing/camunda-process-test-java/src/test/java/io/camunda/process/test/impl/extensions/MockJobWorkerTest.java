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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.process.test.api.mock.VariablesTable;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.client.CamundaClockClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.extension.ConditionalBehaviorEngine;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
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
            new ConditionalBehaviorEngine(),
            () -> new CamundaDataSource(camundaClient));
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
  void shouldOpenJobWorkerWithThenCompleteWithVariablesSupplierFunction() throws Exception {
    // given
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("loopCounter", 1));

    // when
    processTestContext
        .mockJobWorker(JOB_TYPE)
        .thenComplete(jobVariables -> Collections.singletonMap("sampleType", "regolith"));

    // then: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    // and: invoking the handler completes the job with the variables from the supplier
    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newCompleteCommand(activatedJob)
                .variables(Collections.singletonMap("sampleType", "regolith")))
        .send();
  }

  @Test
  void shouldFailJobWhenVariablesSupplierThrows() {
    // given
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("loopCounter", 1));
    final Function<Map<String, Object>, Map<String, Object>> throwingSupplier =
        jobVariables -> {
          throw new IllegalStateException("boom");
        };

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(throwingSupplier);

    // then: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    // and: invoking the handler propagates the exception and never completes the job
    assertThatThrownBy(() -> handlerCaptor.getValue().handle(camundaClient, activatedJob))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
    verify(camundaClient, never()).newCompleteCommand(any(ActivatedJob.class));
  }

  @Test
  void shouldFailJobWhenVariablesSupplierReturnsNull() {
    // given
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("loopCounter", 1));
    final Function<Map<String, Object>, Map<String, Object>> nullSupplier = jobVariables -> null;

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(nullSupplier);

    // then: the client opened the job worker with the expected job type
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    // and: invoking the handler fails with IllegalStateException and never completes the job
    assertThatThrownBy(() -> handlerCaptor.getValue().handle(camundaClient, activatedJob))
        .isInstanceOf(IllegalStateException.class);
    verify(camundaClient, never()).newCompleteCommand(any(ActivatedJob.class));
  }

  @Test
  void shouldCompleteJobWithFirstMatchingRowOfVariablesTable() throws Exception {
    // given: a real JSON mapper so values are normalized the same way as the job's variables
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("loopCounter", 1));

    final VariablesTable variablesTable =
        VariablesTable.when(Collections.singletonMap("loopCounter", 1))
            .thenReturn(Collections.singletonMap("sampleType", "regolith"))
            .when(Collections.singletonMap("loopCounter", 1))
            .thenReturn(Collections.singletonMap("sampleType", "should-not-be-used"));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newCompleteCommand(activatedJob)
                .variables(Collections.singletonMap("sampleType", "regolith")))
        .send();
  }

  @Test
  void shouldIgnoreExtraJobVariablesWhenMatchingVariablesTable() throws Exception {
    // given
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    final Map<String, Object> jobVariables = new HashMap<>();
    jobVariables.put("loopCounter", 1);
    jobVariables.put("item", "regolith-sample");
    when(activatedJob.getVariablesAsMap()).thenReturn(jobVariables);

    final VariablesTable variablesTable =
        VariablesTable.when(Collections.singletonMap("loopCounter", 1))
            .thenReturn(Collections.singletonMap("sampleType", "regolith"));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newCompleteCommand(activatedJob)
                .variables(Collections.singletonMap("sampleType", "regolith")))
        .send();
  }

  @Test
  void shouldCompleteWithEmptyMapWhenNoRowOfVariablesTableMatches() throws Exception {
    // given
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("loopCounter", 99));

    final VariablesTable variablesTable =
        VariablesTable.when(Collections.singletonMap("loopCounter", 1))
            .thenReturn(Collections.singletonMap("sampleType", "regolith"));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(camundaClient.newCompleteCommand(activatedJob).variables(new HashMap<>())).send();
  }

  @Test
  void shouldMatchIntAndLongWhenButNotDecimalForVariablesTable() throws Exception {
    // given: 1 and 1L both match job variable 1, 1.0 does not
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("count", 1));

    final VariablesTable variablesTable =
        VariablesTable.when(Collections.singletonMap("count", 1.0))
            .thenReturn(Collections.singletonMap("matched", "decimal"))
            .when(Collections.singletonMap("count", 1L))
            .thenReturn(Collections.singletonMap("matched", "long"));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then: the decimal row does not match, so the long row (second) wins
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newCompleteCommand(activatedJob)
                .variables(Collections.singletonMap("matched", "long")))
        .send();
  }

  @Test
  void shouldMatchJobVariablesNormalizedWithConfiguredMapperForVariablesTable() throws Exception {
    // given: the configured mapper reads integers as Long, the job variables carry an Integer
    when(camundaClient.getConfiguration().getJsonMapper())
        .thenReturn(
            new CamundaObjectMapper(
                new ObjectMapper().enable(DeserializationFeature.USE_LONG_FOR_INTS)));
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("count", 1));

    final VariablesTable variablesTable =
        VariablesTable.when(Collections.singletonMap("count", 1))
            .thenReturn(Collections.singletonMap("matched", true));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newCompleteCommand(activatedJob)
                .variables(Collections.singletonMap("matched", true)))
        .send();
  }

  @Test
  void shouldMatchEmptyWhenAsCatchAllForVariablesTable() throws Exception {
    // given
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("loopCounter", 42));

    final VariablesTable variablesTable =
        VariablesTable.when(Collections.emptyMap())
            .thenReturn(Collections.singletonMap("sampleType", "default"));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newCompleteCommand(activatedJob)
                .variables(Collections.singletonMap("sampleType", "default")))
        .send();
  }

  @Test
  void shouldNotMatchWhenKeyIsMissingFromJobVariablesForVariablesTable() throws Exception {
    // given
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("other", 1));

    final VariablesTable variablesTable =
        VariablesTable.when(Collections.singletonMap("loopCounter", 1))
            .thenReturn(Collections.singletonMap("sampleType", "regolith"));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then: no row matches, so the job completes without variables
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(camundaClient.newCompleteCommand(activatedJob).variables(new HashMap<>())).send();
  }

  @Test
  void shouldMatchNullValueOnlyAgainstPresentNullJobVariableForVariablesTable() throws Exception {
    // given
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    final Map<String, Object> jobVariables = new HashMap<>();
    jobVariables.put("errorCode", null);
    when(activatedJob.getVariablesAsMap()).thenReturn(jobVariables);

    final Map<String, Object> when = new HashMap<>();
    when.put("errorCode", null);
    final VariablesTable variablesTable =
        VariablesTable.when(when).thenReturn(Collections.singletonMap("handled", true));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newCompleteCommand(activatedJob)
                .variables(Collections.singletonMap("handled", true)))
        .send();
  }

  @Test
  void shouldNotMatchNullValueAgainstMissingKeyForVariablesTable() throws Exception {
    // given: the job has no "errorCode" variable at all
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    when(activatedJob.getVariablesAsMap()).thenReturn(Collections.singletonMap("other", 1));

    final Map<String, Object> when = new HashMap<>();
    when.put("errorCode", null);
    final VariablesTable variablesTable =
        VariablesTable.when(when).thenReturn(Collections.singletonMap("handled", true));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then: no row matches
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(camundaClient.newCompleteCommand(activatedJob).variables(new HashMap<>())).send();
  }

  @Test
  void shouldMatchNestedMapOnlyOnEqualWholeValueForVariablesTable() throws Exception {
    // given
    when(camundaClient.getConfiguration().getJsonMapper()).thenReturn(new CamundaObjectMapper());
    when(activatedJob.getVariablesAsMap())
        .thenReturn(
            Collections.singletonMap("address", Collections.singletonMap("city", "Berlin")));

    final VariablesTable variablesTable =
        VariablesTable.when(
                Collections.singletonMap("address", Collections.singletonMap("city", "Berlin")))
            .thenReturn(Collections.singletonMap("matched", true));

    // when
    processTestContext.mockJobWorker(JOB_TYPE).thenComplete(variablesTable);

    // then
    verify(camundaClient.newWorker().jobType(JOB_TYPE)).handler(handlerCaptor.capture());
    verify(camundaClient.newWorker().jobType(JOB_TYPE).handler(any())).open();

    handlerCaptor.getValue().handle(camundaClient, activatedJob);
    verify(
            camundaClient
                .newCompleteCommand(activatedJob)
                .variables(Collections.singletonMap("matched", true)))
        .send();
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
