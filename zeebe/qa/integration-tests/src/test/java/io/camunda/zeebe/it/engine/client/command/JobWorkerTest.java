/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.grpc.internal.AbstractStream.TransportState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.assertj.core.data.Offset;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ZeebeIntegration
final class JobWorkerTest {

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker zeebe;

  private static GrpcClientRule client;
  private final String jobType = Strings.newRandomValidBpmnId();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    zeebe = new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();
  }

  @BeforeAll
  static void beforeAll() {
    client = new GrpcClientRule(zeebe.newClientBuilder().build());
  }

  @AfterAll
  static void afterAll() {
    client.after();
  }

  @ParameterizedTest
  @MethodSource("provideWorkerConfigurators")
  void shouldActivateJob(final BiFunction<String, JobWorkerBuilderStep3, JobWorker> configurator) {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeJobRetries("5")
                        .zeebeTaskHeader("x", "1")
                        .zeebeTaskHeader("y", "2"))
            .done();
    final var processDefinitionKey = client.deployProcess(process);
    final var processInstanceKey =
        client.createProcessInstance(processDefinitionKey, "{\"a\":1, \"b\":2}");

    // when
    final var jobHandler = new RecordingJobHandler();
    final var startTime = System.currentTimeMillis();
    final var builder =
        client
            .getClient()
            .newWorker()
            .jobType(jobType)
            .handler(jobHandler)
            .name("test")
            .timeout(5000);
    try (final var ignored = configurator.apply(jobType, builder)) {
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandler.getHandledJobs()).hasSize(1));
    }

    // then
    final var job = jobHandler.getHandledJobs().get(0);
    assertThat(job.getType()).isEqualTo(jobType);
    assertThat(job.getRetries()).isEqualTo(5);
    assertThat(job.getDeadline()).isCloseTo(startTime + 5000, Offset.offset(500L));
    assertThat(job.getWorker()).isEqualTo("test");
    assertThat(job.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(job.getBpmnProcessId()).isEqualTo("process");
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getElementId()).isEqualTo("task");
    assertThat(job.getCustomHeaders()).isEqualTo(Map.of("x", "1", "y", "2"));
    assertThat(job.getVariablesAsMap())
        .isEqualTo(
            Map.of(
                "a", 1,
                "b", 2));
  }

  @ParameterizedTest
  @MethodSource("provideWorkerConfigurators")
  void shouldActivateJobsOfDifferentTypes(
      final BiFunction<String, JobWorkerBuilderStep3, JobWorker> configurator) {
    // given
    final var jobX = client.createSingleJob(jobType + "x");
    final var jobY = client.createSingleJob(jobType + "y");
    final var jobHandlerX = new RecordingJobHandler();
    final var jobHandlerY = new RecordingJobHandler();
    final var builderX = client.getClient().newWorker().jobType(jobType + "x").handler(jobHandlerX);
    final var builderY = client.getClient().newWorker().jobType(jobType + "y").handler(jobHandlerY);

    // when
    try (final var ignoredX = configurator.apply(jobType + "x", builderX);
        final var ignoredY = configurator.apply(jobType + "y", builderY)) {
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandlerX.getHandledJobs()).hasSize(1));
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandlerY.getHandledJobs()).hasSize(1));
    }

    // then
    assertThat(jobHandlerX.getHandledJobs())
        .hasSize(1)
        .extracting(ActivatedJob::getKey)
        .contains(jobX);
    assertThat(jobHandlerY.getHandledJobs())
        .hasSize(1)
        .extracting(ActivatedJob::getKey)
        .contains(jobY);
  }

  @ParameterizedTest
  @MethodSource("provideWorkerConfigurators")
  void shouldFetchOnlySpecifiedVariables(
      final BiFunction<String, JobWorkerBuilderStep3, JobWorker> configurator) {
    // given
    client.createSingleJob(jobType, b -> {}, "{\"a\":1, \"b\":2, \"c\":3,\"d\":4}");

    // when
    final var fetchVariables = List.of("a", "b");
    final var jobHandler = new RecordingJobHandler();
    final var builder =
        client
            .getClient()
            .newWorker()
            .jobType(jobType)
            .handler(jobHandler)
            .fetchVariables(fetchVariables);
    try (final var ignored = configurator.apply(jobType, builder)) {
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandler.getHandledJobs()).hasSize(1));
    }

    // then
    final ActivatedJob job = jobHandler.getHandledJobs().get(0);
    assertThat(job.getVariablesAsMap()).isEqualTo(Map.of("a", 1, "b", 2));
  }

  @Test
  void shouldStreamAndActivateJobs() {
    // given - a job created before any stream was registered, i.e. can only be polled
    client.createSingleJob(jobType, b -> {});

    // when - create a worker that streams, and create a new job after the stream is registered
    final var jobHandler = new RecordingJobHandler();
    final var builder =
        client.getClient().newWorker().jobType(jobType).handler(jobHandler).streamEnabled(true);
    try (final var ignored = builder.open()) {
      awaitStreamRegistered(jobType);
      client.createSingleJob(jobType, b -> {});

      // then - expect both jobs to be activated
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandler.getHandledJobs()).hasSize(2));
    }
  }

  @Test
  void shouldRecreateStreamOnGatewayRestart() {
    // given
    final var jobHandler = new RecordingJobHandler();
    final var builder =
        client.getClient().newWorker().jobType(jobType).handler(jobHandler).streamEnabled(true);

    // when
    try (final var ignored = builder.open()) {
      awaitStreamRegistered(jobType);
      zeebe.stop().start().awaitCompleteTopology();
      // need to stream being registered, as otherwise the job will be polled, not streamed
      awaitStreamRegistered(jobType);
      client.createSingleJob(jobType, b -> {});

      // then - expect job to be activated
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandler.getHandledJobs()).hasSize(1));
    }
  }

  @ParameterizedTest
  @MethodSource("provideWorkerConfigurators")
  void shouldNotEscapeCharactersOfInputs(
      final BiFunction<String, JobWorkerBuilderStep3, JobWorker> configurator) {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInput("Hello\nWorld", "newline")
                        .zeebeInput("Hello\rWorld", "carriageReturn")
                        .zeebeInput("Hello\tWorld", "tab"))
            .done();
    final var processDefinitionKey = client.deployProcess(process);
    client.createProcessInstance(processDefinitionKey);

    // when
    final var jobHandler = new RecordingJobHandler();
    final var builder =
        client
            .getClient()
            .newWorker()
            .jobType(jobType)
            .handler(jobHandler)
            .name("test")
            .timeout(5000);
    try (final var ignored = configurator.apply(jobType, builder)) {
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandler.getHandledJobs()).hasSize(1));
    }

    // then
    final var job = jobHandler.getHandledJobs().get(0);
    assertThat(job.getVariablesAsMap())
        .isEqualTo(
            Map.of(
                "tab", "Hello\tWorld",
                "newline", "Hello\nWorld",
                "carriageReturn", "Hello\rWorld"));
  }

  private long createProcessInstance(final String processId, final Map<String, Object> variables) {
    return client
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private static Stream<Named<BiFunction<String, JobWorkerBuilderStep3, JobWorker>>>
      provideWorkerConfigurators() {
    return Stream.of(
        Named.named("polling", (ignored, builder) -> builder.open()),
        Named.named("streaming", JobWorkerTest::prepareStreamingWorker));
  }

  private static JobWorker prepareStreamingWorker(
      final String jobType, final JobWorkerBuilderStep3 builder) {
    final var worker = builder.streamEnabled(true).open();
    awaitStreamRegistered(jobType);
    return worker;
  }

  private static void awaitStreamRegistered(final String jobType) {
    final var actuator = JobStreamActuator.of(zeebe);
    Awaitility.await("until stream is registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveJobType(1, jobType));
  }

  private void awaitStreamRemoved(final String jobType) {
    final var actuator = JobStreamActuator.of(zeebe);
    Awaitility.await("until no streams are registered")
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .doNotHaveJobType(jobType));
  }

  @ParameterizedTest
  @MethodSource("provideWorkerConfigurators")
  void shouldActivateJobWithTags(
      final BiFunction<String, JobWorkerBuilderStep3, JobWorker> configurator) {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType).zeebeJobRetries("5"))
            .done();
    final var processDefinitionKey = client.deployProcess(process);
    final var processInstanceKey =
        client.createProcessInstance(processDefinitionKey, Set.of("tag1", "tag2"));

    // when
    final var jobHandler = new RecordingJobHandler();
    final var startTime = System.currentTimeMillis();
    final var builder =
        client
            .getClient()
            .newWorker()
            .jobType(jobType)
            .handler(jobHandler)
            .name("test")
            .timeout(5000);
    try (final var ignored = configurator.apply(jobType, builder)) {
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandler.getHandledJobs()).hasSize(1));
    }

    // then
    final var job = jobHandler.getHandledJobs().get(0);
    assertThat(job.getType()).isEqualTo(jobType);
    assertThat(job.getRetries()).isEqualTo(5);
    assertThat(job.getDeadline()).isCloseTo(startTime + 5000, Offset.offset(500L));
    assertThat(job.getWorker()).isEqualTo("test");
    assertThat(job.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(job.getBpmnProcessId()).isEqualTo("process");
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getElementId()).isEqualTo("task");
    assertThat(job.getTags()).isEqualTo(Set.of("tag1", "tag2"));
  }

  @Nested
  final class SlowWorkerTest {
    private final String uniqueId = Strings.newRandomValidBpmnId();
    private final CountDownLatch latch = new CountDownLatch(1);
    private final BpmnModelInstance process =
        Bpmn.createExecutableProcess(uniqueId)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(uniqueId))
            .endEvent()
            .done();

    // Use internal gRPC constant to generate enough load to toggle the transport state faster
    private final Map<String, Object> payload =
        Map.of("foo", "bar".repeat(TransportState.DEFAULT_ONREADY_THRESHOLD));

    private final RecordingJobHandler jobHandler = new RecordingJobHandler();

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    @AutoClose
    private JobWorker worker;

    @BeforeEach
    void beforeEach() {
      worker =
          client
              .getClient()
              .newWorker()
              .jobType(uniqueId)
              .handler(
                  (c, j) -> {
                    jobHandler.handle(c, j);
                    latch.await();
                  })
              .maxJobsActive(1)
              .pollInterval(Duration.ofMillis(10))
              .streamEnabled(true)
              .open();

      awaitStreamRegistered(uniqueId);
      client.deployProcess(process);
    }

    @Test
    void shouldYieldJobIfClientIsBlocked() {
      // given
      // disable waiting in the exporter and rely on Awaitility polling
      RecordingExporter.setMaximumWaitTime(0);

      // when
      Awaitility.await("until transport is suspended and jobs are yielded")
          .untilAsserted(
              () -> {
                createProcessInstance(uniqueId, payload);

                // then
                assertThat(RecordingExporter.jobRecords(JobIntent.YIELDED).limit(1)).hasSize(1);
              });
    }

    @Test
    void shouldReceiveNewJobsWhenUnblocked() {
      // given
      final var startedPiKeys = new CopyOnWriteArrayList<Long>();

      // when
      RecordingExporter.await()
          .untilAsserted(
              () -> {
                startedPiKeys.add(createProcessInstance(uniqueId, payload));
                assertThat(RecordingExporter.jobRecords(JobIntent.YIELDED).exists()).isTrue();
              });

      final var firstYieldedPi =
          RecordingExporter.jobRecords(JobIntent.YIELDED)
              .limit(1)
              .findFirst()
              .orElseThrow()
              .getValue()
              .getProcessInstanceKey();
      final var firstYieldedPiIndex = startedPiKeys.indexOf(firstYieldedPi);
      final var yieldedPis =
          new ArrayList<>(startedPiKeys.subList(firstYieldedPiIndex, startedPiKeys.size()));

      // unblock the worker, await the extra buffered jobs (to avoid flakiness), and
      // allow to poll for yielded jobs
      latch.countDown();
      // create a new PI to test that we can also receive new jobs
      startedPiKeys.add(createProcessInstance(uniqueId, payload));

      // then - unblock worker and expect that all jobs have been received
      assertThat(yieldedPis).isNotEmpty();
      // we expect at least 3 jobs: the first job (which always goes through), the second job which
      // triggered the change of transport state by filling the buffers, and the last job that was
      // created after the consumer is unblocked
      assertThat(startedPiKeys).hasSizeGreaterThanOrEqualTo(3);
      Awaitility.await("until all jobs are received")
          .untilAsserted(
              () ->
                  assertThat(jobHandler.getHandledJobs())
                      .extracting(ActivatedJob::getProcessInstanceKey)
                      .hasSameSizeAs(startedPiKeys)
                      .containsAll(startedPiKeys));

      assertThat(RecordingExporter.jobRecords(JobIntent.YIELDED).limit(yieldedPis.size()))
          .map(Record::getValue)
          .extracting(JobRecordValue::getProcessInstanceKey)
          .hasSameSizeAs(yieldedPis)
          .containsExactlyElementsOf(yieldedPis);
    }
  }
}
