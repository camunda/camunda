/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1.JobStream;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1.StreamJobsListener;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.AbstractStream.TransportState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.agrona.LangUtil;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.data.Offset;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@AutoCloseResources
@ZeebeIntegration
final class StreamJobsTest {
  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true);

  @AutoCloseResource private final ZeebeClient client = ZEEBE.newClientBuilder().build();

  @Test
  void shouldStreamJobs() {
    // given
    final var jobs = new ArrayList<ActivatedJob>();
    final var uniqueId = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(uniqueId)
            .startEvent()
            .serviceTask("task01", b -> b.zeebeJobType(uniqueId))
            .serviceTask("task02", b -> b.zeebeJobType(uniqueId))
            .serviceTask("task03", b -> b.zeebeJobType(uniqueId))
            .endEvent()
            .done();
    final StreamJobsListener jobHandler =
        job -> {
          jobs.add(job);
          client.newCompleteCommand(job).send();
        };
    deployProcess(process);

    // when
    final var stream =
        client
            .newStreamJobsCommand()
            .jobType(uniqueId)
            .listener(jobHandler)
            .workerName("streamer")
            .fetchVariables("foo")
            .tenantIds(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .timeout(Duration.ofSeconds(5))
            .open();
    final var initialTime = System.currentTimeMillis();
    final boolean processInstanceCompleted;

    try (stream) {
      awaitStreamRegistered(uniqueId);
      final var processInstanceKey =
          createProcessInstance(uniqueId, Map.of("foo", "bar", "baz", "buz"));
      processInstanceCompleted =
          RecordingExporter.processInstanceRecords()
              .withProcessInstanceKey(processInstanceKey)
              .limitToProcessInstanceCompleted()
              .findFirst()
              .isPresent();
    }

    // then
    assertThat(processInstanceCompleted)
        .as("all tasks were completed to complete the process instance")
        .isTrue();
    assertThat(jobs)
        .hasSize(3)
        .allSatisfy(
            job -> {
              assertThat(job.getWorker()).isEqualTo("streamer");
              assertThat(job.getDeadline()).isCloseTo(initialTime + 5000, Offset.offset(500L));
              assertThat(job.getVariablesAsMap()).isEqualTo(Map.of("foo", "bar"));
            });
  }

  @Test
  void shouldNotInterfereWithPolling() {
    // given
    final var streamedJobs = new ArrayList<ActivatedJob>();
    final var uniqueId = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(uniqueId)
            .startEvent()
            .serviceTask("task01", b -> b.zeebeJobType(uniqueId))
            .endEvent()
            .done();
    final StreamJobsListener streamHandler = streamedJobs::add;
    deployProcess(process);

    // when - create a process instance and wait for the job to be created; this cannot be streamed
    // since the stream doesn't know about older jobs, but it can be polled! The second process
    // instance is guaranteed to stream, because we prefer streaming to polling
    final var firstPIKey = createProcessInstance(uniqueId);
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(firstPIKey)
                .limit(1)
                .exists())
        .as("job for the first process was created")
        .isTrue();

    // open the stream
    final var stream =
        client
            .newStreamJobsCommand()
            .jobType(uniqueId)
            .listener(streamHandler)
            .workerName("stream")
            .open();
    final long secondPIKey;
    try (stream) {
      awaitStreamRegistered(uniqueId);
      secondPIKey = createProcessInstance(uniqueId);
      Awaitility.await("until job has been streamed")
          .untilAsserted(() -> assertThat(streamedJobs).hasSize(1));
    }

    // poll after the newer job was streamed, showing that polling for older jobs work
    final var polledJobs =
        client
            .newActivateJobsCommand()
            .jobType(uniqueId)
            .maxJobsToActivate(2)
            .workerName("poller")
            .send()
            .join();

    // then
    assertThat(polledJobs.getJobs())
        .first(InstanceOfAssertFactories.type(ActivatedJob.class))
        .extracting(ActivatedJob::getProcessInstanceKey)
        .isEqualTo(firstPIKey);
    assertThat(streamedJobs)
        .first(InstanceOfAssertFactories.type(ActivatedJob.class))
        .extracting(ActivatedJob::getProcessInstanceKey)
        .isEqualTo(secondPIKey);
  }

  @Test
  void shouldCompleteStreamOnGatewayClose() {
    // given
    final var uniqueId = Strings.newRandomValidBpmnId();

    // when
    final var stream =
        client
            .newStreamJobsCommand()
            .jobType(uniqueId)
            .listener(ignored -> {})
            .workerName("stream")
            .open();
    awaitStreamRegistered(uniqueId);
    ZEEBE.stop().start().awaitCompleteTopology();

    // then
    assertThat((Future<?>) stream)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .havingCause()
        .asInstanceOf(InstanceOfAssertFactories.throwable(StatusRuntimeException.class))
        .extracting(StatusRuntimeException::getStatus)
        .extracting(Status::getCode)
        .isEqualTo(Code.CANCELLED);
  }

  private void awaitStreamRegistered(final String jobType) {
    final var actuator = JobStreamActuator.of(ZEEBE);
    Awaitility.await("until stream with type '%s' is registered".formatted(jobType))
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveJobType(1, jobType));
  }

  private long createProcessInstance(final String processId) {
    return createProcessInstance(processId, Map.of());
  }

  private long createProcessInstance(final String processId, final Map<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private void deployProcess(final BpmnModelInstance process) {
    client
        .newDeployResourceCommand()
        .addProcessModel(process, "sequence.bpmn")
        .tenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .send()
        .join();
  }

  private void uncheckedLatchAwait(final CountDownLatch latch) {
    try {
      //noinspection ResultOfMethodCallIgnored
      latch.await(5, TimeUnit.MINUTES);
    } catch (final InterruptedException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  @Nested
  @AutoCloseResources
  final class SlowClientTest {
    private final String uniqueId = Strings.newRandomValidBpmnId();
    private final CountDownLatch latch = new CountDownLatch(1);
    private final List<ActivatedJob> jobs = new CopyOnWriteArrayList<>();
    private final BpmnModelInstance process =
        Bpmn.createExecutableProcess(uniqueId)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(uniqueId))
            .endEvent()
            .done();

    // Use internal gRPC constant to generate enough load to toggle the transport state faster
    private final Map<String, Object> payload =
        Map.of("foo", "bar".repeat(TransportState.DEFAULT_ONREADY_THRESHOLD));

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    @AutoCloseResource
    private JobStream stream;

    @BeforeEach
    void beforeEach() {
      stream =
          client
              .newStreamJobsCommand()
              .jobType(uniqueId)
              .listener(
                  job -> {
                    jobs.add(job);
                    uncheckedLatchAwait(latch);
                  })
              .workerName("stream")
              .open();

      awaitStreamRegistered(uniqueId);
      deployProcess(process);
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
      RecordingExporter.setMaximumWaitTime(0);
      Awaitility.await("until transport is suspended and jobs are yielded")
          .untilAsserted(
              () -> {
                startedPiKeys.add(createProcessInstance(uniqueId, payload));
                assertThat(RecordingExporter.jobRecords(JobIntent.YIELDED).limit(1)).hasSize(1);
              });
      RecordingExporter.setMaximumWaitTime(5000);

      final var firstYieldedPi =
          RecordingExporter.jobRecords(JobIntent.YIELDED)
              .limit(1)
              .findFirst()
              .orElseThrow()
              .getValue()
              .getProcessInstanceKey();
      final var firstYieldedPiIndex = startedPiKeys.indexOf(firstYieldedPi);
      final var expectedJobPis = new ArrayList<>(startedPiKeys.subList(0, firstYieldedPiIndex));
      final var yieldedPis = startedPiKeys.subList(firstYieldedPiIndex, startedPiKeys.size());

      // unblock the stream consumer, await the extra buffered jobs (to avoid flakiness), and
      // create a new PI to test that we can receive new jobs
      latch.countDown();
      Awaitility.await("until buffered jobs are received")
          .untilAsserted(() -> assertThat(jobs).hasSameSizeAs(expectedJobPis));
      expectedJobPis.add(createProcessInstance(uniqueId, payload));

      // then - unblock stream consumer and expect jobs for the PIs started before we yielded to
      // have been received, and jobs for those after to not have been received
      assertThat(yieldedPis).isNotEmpty();
      // we expect at least 3 jobs: the first job (which always goes through), the second job which
      // triggered the change of transport state by filling the buffers, and the last job that was
      // created after the consumer is unblocked
      assertThat(expectedJobPis).hasSizeGreaterThanOrEqualTo(3);
      Awaitility.await("until last job is received")
          .untilAsserted(
              () ->
                  assertThat(jobs)
                      .extracting(ActivatedJob::getProcessInstanceKey)
                      .hasSameSizeAs(expectedJobPis)
                      .containsExactlyElementsOf(expectedJobPis));
      assertThat(RecordingExporter.jobRecords(JobIntent.YIELDED).limit(yieldedPis.size()))
          .map(Record::getValue)
          .extracting(JobRecordValue::getProcessInstanceKey)
          .hasSameSizeAs(yieldedPis)
          .containsExactlyElementsOf(yieldedPis);
    }
  }
}
