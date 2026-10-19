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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The checkpoint scheduler completes the handover with one request per partition (spec section
 * 18.4, row 3). A request that is written on one partition and not yet processed on another must
 * not lead to two different handovers: the scheduler repeats the request, the partitions reject the
 * repeats, and both partitions record the same checkpoint H.
 *
 * <p>The scheduler only sends the request once it has queried that H is confirmed and archived on
 * every partition, so a partition that is down cannot be the one that misses it: the query would
 * fail first. The partition that misses it is one that answers the query but does not process the
 * request. This is made deterministic by pausing the processing on the leader of one partition once
 * H is confirmed and archived everywhere, with a backup interval long enough to do so before the
 * scheduler's next tick.
 */
@ZeebeIntegration
final class LogArchiveHandoverPartialCompletionIT {

  private static final int PARTITIONS = 2;
  private static final int BROKERS = 3;
  private static @TempDir Path tempDir;

  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKERS)
          .withPartitionsCount(PARTITIONS)
          .withReplicationFactor(BROKERS)
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              (id, broker) ->
                  LogArchiveBucket.enableLogArchive(
                      broker
                          .withRecordingExporter(true)
                          .withUnifiedConfig(
                              cfg -> {
                                LogArchiveBucket.configureStore(cfg, basePath);
                                final var backup = cfg.getData().getPrimaryStorage().getBackup();
                                backup.setContinuous(true);
                                backup.setSchedule("PT15S");
                              })))
          .build();

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    RecordingExporter.reset();
    cluster.start().awaitCompleteTopology();
    bucket = new LogArchiveBucket(basePath);
  }

  @AfterEach
  void tearDown() {
    bucket.close();
  }

  @Test
  void shouldRecordTheSameHandoverOnAllPartitionsWhenOnePartitionLagsBehind() {
    // given -- data on both partitions and a snapshot on every broker, so the scheduler can hand
    // over
    final var lagging = leaderOfPartitionTwo();
    try (final var client = cluster.newClientBuilder().build()) {
      final long process = deployProcess(client);
      IntStream.range(0, 20).forEach(i -> createInstance(client, process));
    }
    cluster.brokers().values().forEach(broker -> PartitionsActuator.of(broker).takeSnapshot());

    // when -- H is confirmed and archived on both partitions, the leader of partition 2 stops
    // processing, so the scheduler's request is written there but not recorded
    final long handover =
        await("a handover backup is confirmed and archived on every partition")
            .atMost(Duration.ofMinutes(2))
            .until(
                LogArchiveHandoverPartialCompletionIT::confirmedAndArchivedHandover,
                id -> id.isPresent())
            .orElseThrow();
    assertThat(handoversCompleted()).as("no handover is recorded yet").isEmpty();
    PartitionsActuator.of(lagging).pauseProcessing();

    // then -- partition 1 records H, and the scheduler repeats the request to partition 2
    await("partition 1 records the handover while partition 2 lags")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(handoversCompleted()).containsExactly(Map.entry(1, handover));
              assertThat(completeHandoverCommands(2))
                  .as("requests written on partition 2 while it does not process them")
                  .hasSizeGreaterThanOrEqualTo(2);
            });

    // and -- when partition 2 recovers, it records the same H, and rejects the repeated requests
    PartitionsActuator.of(lagging).resumeProcessing();
    await("partition 2 records the same handover")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(handoversCompleted())
                    .containsExactlyInAnyOrderEntriesOf(Map.of(1, handover, 2, handover)));
    await("the repeated requests are rejected")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.getRecords().stream()
                            .filter(r -> r.getPartitionId() == 2)
                            .filter(r -> r.getRecordType() == RecordType.COMMAND_REJECTION)
                            .filter(r -> r.getIntent() == LogArchiveIntent.COMPLETE_HANDOVER))
                    .isNotEmpty());

    // and -- no second handover was taken, and scheduled backups continue after H
    assertThat(checkpoints(CheckpointIntent.CREATED, CheckpointType.HANDOVER))
        .extracting(r -> r.getValue().getCheckpointId())
        .containsOnly(handover);
    await("a scheduled backup is taken on every partition after the handover")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(checkpoints(CheckpointIntent.CREATED, CheckpointType.SCHEDULED_BACKUP))
                    .filteredOn(r -> r.getValue().getCheckpointId() > handover)
                    .extracting(Record::getPartitionId)
                    .contains(1, 2));
    assertThat(handoversCompleted())
        .as("one handover per partition")
        .containsExactlyInAnyOrderEntriesOf(Map.of(1, handover, 2, handover));

    // and -- the handover files exist for both partitions, and the archives hold H exactly once
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
    cluster.shutdown();
    IntStream.rangeClosed(1, PARTITIONS)
        .forEach(
            p ->
                assertThat(bucket.readAndVerify(TIMELINE, p).commits())
                    .filteredOn(entry -> entry.type() == CommitEntry.Type.HANDOVER)
                    .extracting(CommitEntry::handover)
                    .extracting(HandoverJson::checkpointId)
                    .containsExactly(handover));
  }

  /** The leader of partition 2, which must not lead partition 1 as well. */
  private TestStandaloneBroker leaderOfPartitionTwo() {
    final var leader = cluster.leaderForPartition(2);
    assertThat(cluster.leaderForPartition(1))
        .as("pausing the leader of partition 2 must not pause partition 1")
        .isNotSameAs(leader);
    return leader;
  }

  /**
   * The newest HANDOVER checkpoint that is confirmed on every partition, once the log archive of
   * every partition archived up to its position; the scheduler only completes it then.
   */
  private static Optional<Long> confirmedAndArchivedHandover() {
    final var confirmed =
        checkpoints(CheckpointIntent.CONFIRMED_BACKUP, CheckpointType.HANDOVER).stream()
            .collect(
                Collectors.groupingBy(
                    r -> r.getValue().getCheckpointId(),
                    Collectors.toMap(
                        Record::getPartitionId,
                        r -> r.getValue().getCheckpointPosition(),
                        (a, b) -> a)));
    final var archived = archivedPositions();
    return confirmed.entrySet().stream()
        .filter(e -> e.getValue().keySet().equals(Set.of(1, 2)))
        .filter(
            e ->
                e.getValue().entrySet().stream()
                    .allMatch(p -> archived.getOrDefault(p.getKey(), -1L) >= p.getValue()))
        .map(Map.Entry::getKey)
        .max(Long::compare);
  }

  private static Map<Integer, Long> archivedPositions() {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.LOG_ARCHIVE)
        .filter(r -> r.getIntent() == LogArchiveIntent.LOG_ARCHIVED)
        .collect(
            Collectors.toMap(
                Record::getPartitionId,
                r -> ((LogArchiveRecordValue) r.getValue()).getArchivedPosition(),
                Math::max));
  }

  /** Per partition, the checkpoint id of its HANDOVER_COMPLETED event. */
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

  private static List<Record<?>> completeHandoverCommands(final int partition) {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getPartitionId() == partition)
        .filter(r -> r.getRecordType() == RecordType.COMMAND)
        .filter(r -> r.getIntent() == LogArchiveIntent.COMPLETE_HANDOVER)
        .<Record<?>>map(r -> r)
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Record<CheckpointRecordValue>> checkpoints(
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
