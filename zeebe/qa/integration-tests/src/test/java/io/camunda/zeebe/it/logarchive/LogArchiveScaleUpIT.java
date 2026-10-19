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
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveRecordsProcessor;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.protocol.record.value.management.LogArchiveRecordValue;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A partition added by a scale-up after the handover from continuous backup to the log archive is
 * archive-only from birth (spec section 18.4): it is activated, records the handover from its
 * start, scheduled backups go on without old-style uploads, and a restore after the scale-up
 * restores it from its archive, while a restore before the scale-up leaves it out.
 *
 * <p>A single broker scales from two to three partitions. The data of an instance phase is told
 * apart by the phase variable of its jobs, so each restore proves it holds exactly the data up to
 * its checkpoint, on every partition.
 */
@ZeebeIntegration
final class LogArchiveScaleUpIT {

  private static final int PARTITIONS = 2;
  private static final int SCALED_PARTITIONS = 3;
  private static final int PHASE_SIZE = 6;
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path sourceBucket = testDirectory.resolve("source-bucket");
  private final String jobType = "job-" + UUID.randomUUID();
  private final String processId = "process-" + UUID.randomUUID();

  @TestZeebe(partitionCount = PARTITIONS)
  private final TestStandaloneBroker source =
      brokerConfiguredFor(
              new TestStandaloneBroker().withRecordingExporter(true),
              sourceBucket,
              testDirectory.resolve("source-data"),
              "PT5S")
          .withUnifiedConfig(
              cfg -> {
                // faster gossip, so the scale-up reaches the gateway's routing quickly
                cfg.getCluster().getMembership().setSyncInterval(Duration.ofSeconds(1));
                cfg.getCluster().getMembership().setGossipInterval(Duration.ofMillis(500));
              });

  @Test
  void shouldMakeAPartitionAddedAfterTheHandoverArchiveOnly() {
    // given -- a completed handover H, and an archive-only backup before the scale-up
    final var backups = BackupActuator.of(source);
    final long process;
    final long beforeScaleUp;
    try (final var client = source.newClientBuilder().build()) {
      process = deployProcess(client);
      createInstances(client, process, "a", PHASE_SIZE);
      PartitionsActuator.of(source).takeSnapshot();
      final long handover = awaitHandover();
      createInstances(client, process, "b", PHASE_SIZE);
      beforeScaleUp = takeBackup(backups);
      assertThat(beforeScaleUp).isGreaterThan(handover);
    }

    // when -- the cluster scales up to a third partition
    scaleUp();
    PartitionsActuator.of(source).takeSnapshot();

    // then -- the new partition is activated and records the same handover from its start
    final var handovers = awaitHandoversCompleted(SCALED_PARTITIONS);
    assertThat(handovers.values().stream().map(LogArchiveRecordValue::getHandoverCheckpointId))
        .as("one handover checkpoint for all partitions")
        .containsOnly(handovers.get(1).getHandoverCheckpointId());
    assertThat(handovers.get(3).getHandoverPosition())
        .isEqualTo(LogArchiveRecordsProcessor.HANDOVER_POSITION_FROM_START);
    assertThat(handoverFile(sourceBucket, 3, handovers.get(3).getHandoverCheckpointId()))
        .isRegularFile();

    // and -- scheduled backups go on on every partition, without old-style uploads
    final long scheduled = awaitScheduledBackupAfterTheHandoverOf(3);
    await("backup " + scheduled + " is completed")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(backups.status(scheduled).getState()).isEqualTo(StateCode.COMPLETED));

    // and -- a backup after the scale-up covers data on the new partition
    final long afterScaleUp;
    try (final var client = source.newClientBuilder().build()) {
      createInstances(client, process, "c", PHASE_SIZE);
      afterScaleUp = takeBackup(backups);
      createInstances(client, process, "d", PHASE_SIZE);
    }
    source.stop();
    IntStream.rangeClosed(1, SCALED_PARTITIONS)
        .forEach(
            partition -> {
              assertThat(oldBackup(sourceBucket, partition, scheduled))
                  .as("no old-style backup %d of partition %d", scheduled, partition)
                  .doesNotExist();
              assertThat(oldBackup(sourceBucket, partition, afterScaleUp))
                  .as("no old-style backup %d of partition %d", afterScaleUp, partition)
                  .doesNotExist();
            });

    // and -- a restore after the scale-up restores every partition from the archive
    assertRestore(
        "after-scale-up",
        afterScaleUp,
        SCALED_PARTITIONS,
        Map.of("a", PHASE_SIZE, "b", PHASE_SIZE, "c", PHASE_SIZE));

    // and -- a restore before the scale-up leaves the new partition out
    assertRestore(
        "before-scale-up", beforeScaleUp, PARTITIONS, Map.of("a", PHASE_SIZE, "b", PHASE_SIZE));
  }

  private void scaleUp() {
    final var cluster = ClusterActuator.of(source);
    final var scaleUp =
        cluster.patchCluster(
            new ClusterConfigPatchRequest()
                .partitions(
                    new ClusterConfigPatchRequestPartitions()
                        .count(SCALED_PARTITIONS)
                        .replicationFactor(1)),
            false,
            false);
    await("the cluster is scaled up to %d partitions".formatted(SCALED_PARTITIONS))
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(cluster)
                    .hasCompletedChanges(scaleUp)
                    .doesNotHavePendingChanges());
  }

  /** Waits until the partitions before the scale-up completed the handover and returns its id. */
  private static long awaitHandover() {
    final var handovers = awaitHandoversCompleted(PARTITIONS);
    return handovers.values().iterator().next().getHandoverCheckpointId();
  }

  /** Per partition, its HANDOVER_COMPLETED event, once there is one on {@code count} partitions. */
  private static Map<Integer, LogArchiveRecordValue> awaitHandoversCompleted(final int count) {
    return await("the handover is completed on %d partitions".formatted(count))
        .atMost(Duration.ofMinutes(2))
        .until(LogArchiveScaleUpIT::handoversCompleted, completed -> completed.size() == count);
  }

  private static Map<Integer, LogArchiveRecordValue> handoversCompleted() {
    final var handovers = new TreeMap<Integer, LogArchiveRecordValue>();
    RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.LOG_ARCHIVE)
        .filter(r -> r.getIntent() == LogArchiveIntent.HANDOVER_COMPLETED)
        .forEach(
            r -> handovers.putIfAbsent(r.getPartitionId(), (LogArchiveRecordValue) r.getValue()));
    return handovers;
  }

  /**
   * The first scheduled backup the partition created after it recorded the handover, once every
   * partition created it.
   */
  private static long awaitScheduledBackupAfterTheHandoverOf(final int partition) {
    final long handoverPosition =
        RecordingExporter.getRecords().stream()
            .filter(r -> r.getPartitionId() == partition)
            .filter(r -> r.getIntent() == LogArchiveIntent.HANDOVER_COMPLETED)
            .findFirst()
            .orElseThrow()
            .getPosition();
    final long scheduled =
        await("a scheduled backup is taken on partition %d after its handover".formatted(partition))
            .atMost(Duration.ofSeconds(60))
            .until(
                () ->
                    createdScheduledBackups().stream()
                        .filter(r -> r.getPartitionId() == partition)
                        .filter(r -> r.getPosition() > handoverPosition)
                        .map(r -> r.getValue().getCheckpointId())
                        .findFirst(),
                Optional::isPresent)
            .orElseThrow();
    await("scheduled backup %d is created on every partition".formatted(scheduled))
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(createdScheduledBackups())
                    .filteredOn(r -> r.getValue().getCheckpointId() == scheduled)
                    .extracting(Record::getPartitionId)
                    .contains(1, 2, 3));
    return scheduled;
  }

  @SuppressWarnings("unchecked")
  private static List<Record<CheckpointRecordValue>> createdScheduledBackups() {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.CHECKPOINT)
        .filter(r -> r.getIntent() == CheckpointIntent.CREATED)
        .map(r -> (Record<CheckpointRecordValue>) r)
        .filter(r -> r.getValue().getCheckpointType() == CheckpointType.SCHEDULED_BACKUP)
        .toList();
  }

  /**
   * Restores a copy of the bucket to the checkpoint with the restore application configured for the
   * scaled-up partitions, checks that exactly the expected partitions are restored from the
   * archive, and that the restored broker holds exactly the expected jobs.
   */
  private void assertRestore(
      final String name,
      final long checkpointId,
      final int restoredPartitions,
      final Map<String, Integer> expectedPhases) {
    final var restoredBucket = copyBucket(name + "-bucket");
    final var data = testDirectory.resolve(name + "-restored-data");
    restore(restoredBucket, data, app -> app.withBackupId(checkpointId));
    IntStream.rangeClosed(1, restoredPartitions)
        .forEach(
            partition ->
                assertThat(timelineOf(data, partition))
                    .as("timeline of partition %d restored for %s", partition, name)
                    .isEqualTo(TIMELINE + ".r" + checkpointId));
    IntStream.rangeClosed(restoredPartitions + 1, SCALED_PARTITIONS)
        .forEach(
            partition ->
                assertThat(data.resolve("default/partitions/" + partition))
                    .as("partition %d is not restored for %s", partition, name)
                    .doesNotExist());

    RecordingExporter.reset();
    try (final var restored = startBroker(restoredBucket, data, restoredPartitions);
        final var client = restored.newClientBuilder().build()) {
      assertThat(activateAll(client, expectedPhases.values().stream().mapToInt(i -> i).sum()))
          .as("jobs of the cluster restored for %s", name)
          .isEqualTo(expectedPhases);
    }
  }

  private void restore(
      final Path restoredBucket, final Path data, final Consumer<TestRestoreApp> selection) {
    final var app =
        brokerConfiguredFor(new TestRestoreApp(), restoredBucket, data, null)
            .withUnifiedConfig(
                cfg -> {
                  cfg.getCluster().setPartitionCount(SCALED_PARTITIONS);
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                });
    selection.accept(app);
    try {
      app.start();
    } finally {
      app.close();
    }
  }

  /** Starts a broker configured for the scaled-up partitions on restored data. */
  private TestStandaloneBroker startBroker(
      final Path bucketPath, final Path data, final int restoredPartitions) {
    final var broker =
        brokerConfiguredFor(
                new TestStandaloneBroker().withRecordingExporter(true), bucketPath, data, null)
            .withUnifiedConfig(cfg -> cfg.getCluster().setPartitionCount(SCALED_PARTITIONS));
    broker.start().awaitCompleteTopology(1, restoredPartitions, 1, Duration.ofSeconds(60));
    return broker;
  }

  private static <T extends TestSpringApplication<T>> T brokerConfiguredFor(
      final T application, final Path bucketPath, final Path data, final String backupSchedule) {
    return LogArchiveBucket.enableLogArchive(application)
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().setPartitionCount(PARTITIONS);
              LogArchiveBucket.configureStore(cfg, bucketPath);
              configureBackup(cfg, data, backupSchedule);
            });
  }

  private static void configureBackup(
      final Camunda cfg, final Path data, final String backupSchedule) {
    final var primaryStorage = cfg.getData().getPrimaryStorage();
    primaryStorage.setDirectory(data.toAbsolutePath().toString());
    if (backupSchedule != null) {
      primaryStorage.getBackup().setContinuous(true);
      primaryStorage.getBackup().setSchedule(backupSchedule);
    }
  }

  private long takeBackup(final BackupActuator backups) {
    final long backupId = backups.take().getBackupId();
    await("backup " + backupId + " is completed")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(backups.status(backupId).getState()).isEqualTo(StateCode.COMPLETED));
    return backupId;
  }

  /** Activates the jobs until the expected number is found and counts them by phase. */
  private Map<String, Integer> activateAll(final CamundaClient client, final int expected) {
    final var activated = new ArrayList<ActivatedJob>();
    await("the expected jobs are activated")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              activated.addAll(activateJobs(client));
              assertThat(activated).hasSize(expected);
            });
    assertThat(activateJobs(client)).as("no further job").isEmpty();
    final var phases = new TreeMap<String, Integer>();
    activated.forEach(
        job -> phases.merge((String) job.getVariablesAsMap().get("phase"), 1, Integer::sum));
    return phases;
  }

  private List<ActivatedJob> activateJobs(final CamundaClient client) {
    return client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(100)
        .fetchVariables("phase")
        .requestTimeout(Duration.ofSeconds(2))
        .send()
        .join()
        .getJobs();
  }

  private long deployProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static void createInstances(
      final CamundaClient client, final long process, final String phase, final int count) {
    for (int i = 0; i < count; i++) {
      client
          .newCreateInstanceCommand()
          .processDefinitionKey(process)
          .variables(Map.of("phase", phase))
          .send()
          .join();
    }
  }

  private static String timelineOf(final Path data, final int partition) {
    try {
      return Files.readString(
          data.resolve("default/partitions/" + partition)
              .resolve(LogArchiveRestore.TIMELINE_FILE_NAME));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path handoverFile(final Path bucketPath, final int partition, final long id) {
    return bucketPath.resolve("metadata/%d/handover/%d.json".formatted(partition, id));
  }

  private static Path oldBackup(final Path bucketPath, final int partition, final long id) {
    return bucketPath.resolve("manifests/%d/%d".formatted(partition, id));
  }

  private Path copyBucket(final String name) {
    final var copy = testDirectory.resolve(name);
    try (final Stream<Path> files = Files.walk(sourceBucket)) {
      for (final var path : files.toList()) {
        final var target = copy.resolve(sourceBucket.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(target);
        } else {
          Files.copy(path, target);
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return copy;
  }
}
