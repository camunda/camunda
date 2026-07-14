/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance coverage for the experimental layered state store flag
 * (zeebe.broker.experimental.engine.layeredState.enabled): with buffered state and memory-driven
 * (buffer-pressure ladder) persist rounds — there is no periodic persist cadence — timers still
 * fire promptly, through the asynchronous due-date checker scanning read views refreshed before
 * each execution; instances complete, snapshots contain a consistent cut (the pre-snapshot persist
 * round), and the broker recovers across a restart.
 */
@Timeout(300)
@ZeebeIntegration
final class LayeredStateStandaloneBrokerTest {

  private static final String PROCESS_ID = "layered-state-timer-process";

  @TempDir private Path workingDirectory;

  private TestStandaloneBroker broker;
  private CamundaClient client;

  @BeforeEach
  void setUp() {
    RecordingExporter.reset();
    broker =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
            .withUnauthenticatedAccess()
            // keep the data directory across restarts so recovery is exercised for real
            .withWorkingDirectory(workingDirectory)
            .withProperty("zeebe.broker.experimental.engine.layeredState.enabled", "true")
            // a tiny buffered-bytes budget so the test exercises several size-triggered
            // (buffer-pressure ladder) persist rounds — there is no periodic persist cadence
            .withProperty("zeebe.broker.experimental.engine.layeredState.maxBufferedBytes", "4KB")
            // tiny slices so every round exercises the paced multi-slice drain with its
            // anchor-carrying final slice, not just the single-batch degenerate case
            .withProperty(
                "zeebe.broker.experimental.engine.layeredState.persistMinSliceBytes", "64B");
    broker.start().awaitCompleteTopology();
    client = newClient();
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietClose(client);
    if (broker != null) {
      broker.stop();
    }
  }

  @Test
  void shouldCompleteTimerProcessAndRecoverAfterRestart() {
    // given a deployed process whose only work is a short timer
    deployTimerProcess();

    // when an instance is started
    final long firstInstanceKey = createInstance();

    // then the timer fires and the instance completes although state commits are buffered
    awaitInstanceCompleted(firstInstanceKey);

    // and the exporter position advances although it is written through the exporter ownership
    // domain (buffered on the exporter director's actor, drained by its persist cadence)
    awaitExportedPositionAdvanced();

    // when a snapshot is forced (drains the buffered state in a pre-snapshot persist round)
    // and the broker restarts from that snapshot plus log replay
    takeSnapshot();
    broker.stop();
    CloseHelper.quietClose(client);
    broker.start().awaitCompleteTopology();
    client = newClient();

    // then the recovered state still serves the deployed process, new instances complete, and
    // the exporter resumes from the recovered (persisted) position and advances again
    final long secondInstanceKey = createInstance();
    awaitInstanceCompleted(secondInstanceKey);
    awaitExportedPositionAdvanced();
  }

  @Test
  void shouldTriggerTimerPromptlyThroughReadViews() {
    // given a deployed process whose only work is a 2s timer
    deployTimerProcess();

    // when an instance is started, the asynchronous due-date checker picks the timer up from a
    // read view of the buffered state
    final long processInstanceKey = createInstance();
    final Record<TimerRecordValue> triggered =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // then the timer fires promptly: the buffered state is frozen into a fresh view right before
    // the checker executes, so the lateness is bounded by the checker's resolution (100ms) plus
    // the schedule service's poll interval (~1s) and scheduling slack. The bound guards the
    // failure mode — a checker that lost view freshness would fire only after a barrier-forced
    // drain, far beyond it — without tripping on ordinary scheduling jitter on a loaded machine
    final long lateness = triggered.getTimestamp() - triggered.getValue().getDueDate();
    assertThat(lateness)
        .describedAs("timer lateness (trigger time - due date) in ms")
        .isBetween(0L, 2_000L);
    awaitInstanceCompleted(processInstanceKey);
  }

  private CamundaClient newClient() {
    return broker.newClientBuilder().preferRestOverGrpc(false).build();
  }

  private void deployTimerProcess() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent("timer", timer -> timer.timerWithDuration("PT2S"))
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(process, PROCESS_ID + ".bpmn").send().join();
  }

  private long createInstance() {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private void awaitInstanceCompleted(final long processInstanceKey) {
    Awaitility.await("until process instance %d completed".formatted(processInstanceKey))
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.processInstanceRecords(
                                ProcessInstanceIntent.ELEMENT_COMPLETED)
                            .withProcessInstanceKey(processInstanceKey)
                            .withElementType(BpmnElementType.PROCESS)
                            .exists())
                    .isTrue());
  }

  private void awaitExportedPositionAdvanced() {
    final PartitionsActuator partitions = PartitionsActuator.of(broker);
    Awaitility.await("until the exported position advanced")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final Long exportedPosition = partitions.query().get(1).exportedPosition();
              assertThat(exportedPosition).isNotNull().isPositive();
            });
  }

  private void takeSnapshot() {
    final PartitionsActuator partitions = PartitionsActuator.of(broker);
    partitions.takeSnapshot();
    Awaitility.await("until a snapshot was committed")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(() -> assertThat(partitions.query().get(1).snapshotId()).isNotNull());
  }
}
