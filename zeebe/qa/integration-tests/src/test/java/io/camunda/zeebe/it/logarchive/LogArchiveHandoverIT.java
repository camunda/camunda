/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import static io.camunda.zeebe.it.logarchive.LogArchiveBucket.TIMELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.HandoverJson;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * With continuous backup on schedule and the log archive enabled, the checkpoint scheduler hands
 * over to the log archive by itself (spec section 18.4): once every partition has archived its last
 * backup and a snapshot, it takes a HANDOVER backup H, completes the handover on every partition
 * once H is confirmed and archived, and then keeps taking scheduled backups as restore points.
 */
@ZeebeIntegration
final class LogArchiveHandoverIT {

  private static final int PARTITIONS = 2;
  private static @TempDir Path tempDir;

  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());

  @TestZeebe(partitionCount = PARTITIONS)
  private final TestStandaloneBroker broker =
      LogArchiveBucket.enableLogArchive(
          new TestStandaloneBroker()
              .withRecordingExporter(true)
              .withUnifiedConfig(
                  cfg -> {
                    cfg.getCluster().setPartitionCount(PARTITIONS);
                    LogArchiveBucket.configureStore(cfg, basePath);
                    final var backup = cfg.getData().getPrimaryStorage().getBackup();
                    backup.setContinuous(true);
                    backup.setSchedule("PT5S");
                  }));

  private LogArchiveBucket bucket;
  private PartitionsActuator partitions;

  @BeforeEach
  void setUp() {
    RecordingExporter.reset();
    bucket = new LogArchiveBucket(basePath);
    partitions = PartitionsActuator.of(broker);
  }

  @AfterEach
  void tearDown() {
    bucket.close();
  }

  @Test
  void shouldHandOverToTheLogArchiveAndKeepTakingScheduledBackups() {
    // given -- something to process on every partition, and a snapshot to archive; the snapshot
    // period of a broker is at least a minute, so the snapshot is taken on demand
    try (final var client = broker.newClientBuilder().build()) {
      final long process = deployProcess(client);
      IntStream.range(0, 20).forEach(i -> createInstance(client, process));
    }
    partitions.takeSnapshot();

    // when -- the scheduler takes a HANDOVER backup and completes the handover
    final var handovers =
        await("the handover is completed on every partition")
            .atMost(Duration.ofMinutes(2))
            .until(
                LogArchiveHandoverIT::handoversCompleted,
                completed -> completed.size() == PARTITIONS);

    // then -- one handover checkpoint H for all partitions, a confirmed HANDOVER backup
    assertThat(handovers.values().stream().distinct())
        .as("one handover checkpoint for all partitions")
        .hasSize(1);
    final long handover = handovers.values().iterator().next();
    assertThat(confirmedBackups(CheckpointType.HANDOVER))
        .as("H is a confirmed HANDOVER backup on every partition")
        .filteredOn(r -> r.getValue().getCheckpointId() == handover)
        .extracting(Record::getPartitionId)
        .containsExactlyInAnyOrder(1, 2);

    // and -- the old side records H next to the old backups of every partition
    await("the old side holds the handover file of every partition")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                IntStream.rangeClosed(1, PARTITIONS)
                    .forEach(
                        p ->
                            assertThat(
                                    basePath.resolve(
                                        "metadata/%d/handover/%d.json".formatted(p, handover)))
                                .isRegularFile()));

    // and -- scheduled backups are still taken after the handover
    await("a scheduled backup is taken on every partition after the handover")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(createdCheckpoints(CheckpointType.SCHEDULED_BACKUP))
                    .filteredOn(r -> r.getValue().getCheckpointId() > handover)
                    .extracting(Record::getPartitionId)
                    .contains(1, 2));

    // and -- the log archive of every partition holds the HANDOVER commit entry for H
    broker.stop();
    IntStream.rangeClosed(1, PARTITIONS)
        .forEach(
            p -> {
              final var archive = bucket.readAndVerify(TIMELINE, p);
              assertThat(archive.commits())
                  .filteredOn(entry -> entry.type() == CommitEntry.Type.HANDOVER)
                  .extracting(CommitEntry::handover)
                  .extracting(HandoverJson::checkpointId)
                  .containsExactly(handover);
            });
  }

  /** Per partition, the handover checkpoint id of its HANDOVER_COMPLETED event. */
  private static Map<Integer, Long> handoversCompleted() {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.LOG_ARCHIVE)
        .filter(r -> r.getIntent() == LogArchiveIntent.HANDOVER_COMPLETED)
        .collect(
            Collectors.toMap(
                Record::getPartitionId,
                r -> ((LogArchiveRecordValue) r.getValue()).getHandoverCheckpointId(),
                (a, b) -> a));
  }

  private static List<Record<CheckpointRecordValue>> confirmedBackups(final CheckpointType type) {
    return checkpointRecords(CheckpointIntent.CONFIRMED_BACKUP, type);
  }

  private static List<Record<CheckpointRecordValue>> createdCheckpoints(final CheckpointType type) {
    return checkpointRecords(CheckpointIntent.CREATED, type);
  }

  @SuppressWarnings("unchecked")
  private static List<Record<CheckpointRecordValue>> checkpointRecords(
      final CheckpointIntent intent, final CheckpointType type) {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.CHECKPOINT)
        .filter(r -> r.getIntent() == intent)
        .map(r -> (Record<CheckpointRecordValue>) r)
        .filter(r -> r.getValue().getCheckpointType() == type)
        .toList();
  }

  private static long deployProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process").startEvent().endEvent().done(), "process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static void createInstance(final CamundaClient client, final long process) {
    client.newCreateInstanceCommand().processDefinitionKey(process).send().join();
  }
}
