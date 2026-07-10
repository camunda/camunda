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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
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
 * (zeebe.broker.experimental.engine.layeredState.enabled): with buffered state and periodic persist
 * rounds, timers still fire, instances complete, snapshots contain a consistent cut (the
 * pre-snapshot persist round), and the broker recovers across a restart.
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
            // short interval so the test exercises several periodic persist rounds
            .withProperty("zeebe.broker.experimental.engine.layeredState.persistInterval", "500ms");
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

    // when a snapshot is forced (drains the buffered state in a pre-snapshot persist round)
    // and the broker restarts from that snapshot plus log replay
    takeSnapshot();
    broker.stop();
    CloseHelper.quietClose(client);
    broker.start().awaitCompleteTopology();
    client = newClient();

    // then the recovered state still serves the deployed process and new instances complete
    final long secondInstanceKey = createInstance();
    awaitInstanceCompleted(secondInstanceKey);
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

  private void takeSnapshot() {
    final PartitionsActuator partitions = PartitionsActuator.of(broker);
    partitions.takeSnapshot();
    Awaitility.await("until a snapshot was committed")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(() -> assertThat(partitions.query().get(1).snapshotId()).isNotNull());
  }
}
