/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.exporter;

import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(2 * 60) // 2 minutes
@ZeebeIntegration
final class ExporterReplayTest {
  private static final String EXPORTER_ID = "replay-test-exporter";

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(1)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withBrokerConfig(
              broker ->
                  broker.withExporter(
                      EXPORTER_ID, config -> config.setClassName(TestExporter.class.getName())))
          .build();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void setUp() {
    TestExporter.reset();

    client = cluster.newClientBuilder().build();

    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("processId").startEvent().endEvent().done(),
            "process.bpmn")
        .send()
        .join();
  }

  @Test
  void shouldReplayRecordsAfterRestartWhenExporterRequestsReplay() {
    // given
    createProcessInstances(3);

    final var recordsBeforeRestart =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(TestExporter.EXPORTED_RECORD_POSITIONS::size, hasStableValue());

    assertThat(recordsBeforeRestart).isGreaterThan(0);

    final var positionsBeforeRestart = List.copyOf(TestExporter.EXPORTED_RECORD_POSITIONS);
    TestExporter.prepareReplayFrom(positionsBeforeRestart.getFirst() - 1);

    // when
    cluster.shutdown();
    cluster.start().awaitCompleteTopology();

    // then
    Awaitility.await("replay should be requested and records should be replayed")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(TestExporter.REPLAY_REQUESTED).isTrue();
              assertThat(TestExporter.EXPORTED_RECORD_POSITIONS)
                  .describedAs("Exporter should receive replayed records after restart")
                  .containsAll(positionsBeforeRestart);
            });
  }

  private void createProcessInstances(final int count) {
    for (int i = 0; i < count; i++) {
      client.newCreateInstanceCommand().bpmnProcessId("processId").latestVersion().send().join();
    }
  }

  public static final class TestExporter implements Exporter {
    static final List<Long> EXPORTED_RECORD_POSITIONS = new CopyOnWriteArrayList<>();
    static final AtomicBoolean REPLAY_REQUESTED = new AtomicBoolean();

    private static final AtomicBoolean REQUEST_REPLAY_ON_OPEN = new AtomicBoolean(false);
    private static final AtomicLong REPLAY_FROM_POSITION = new AtomicLong(-1L);

    private Controller controller;

    @Override
    public void configure(final Context context) throws Exception {
      // no configuration required
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;

      if (REQUEST_REPLAY_ON_OPEN.compareAndSet(true, false)) {
        final long replayFromPosition = REPLAY_FROM_POSITION.get();
        if (replayFromPosition > 0) {
          REPLAY_REQUESTED.set(controller.requestReplay(replayFromPosition));
        }
      }
    }

    @Override
    public void export(final Record<?> record) {
      EXPORTED_RECORD_POSITIONS.add(record.getPosition());
      controller.updateLastExportedRecordPosition(record.getPosition());
    }

    static void prepareReplayFrom(final long fromPosition) {
      EXPORTED_RECORD_POSITIONS.clear();
      REPLAY_FROM_POSITION.set(fromPosition);
      REQUEST_REPLAY_ON_OPEN.set(true);
      REPLAY_REQUESTED.set(false);
    }

    static void reset() {
      EXPORTED_RECORD_POSITIONS.clear();
      REPLAY_FROM_POSITION.set(-1L);
      REQUEST_REPLAY_ON_OPEN.set(false);
      REPLAY_REQUESTED.set(false);
    }
  }
}
