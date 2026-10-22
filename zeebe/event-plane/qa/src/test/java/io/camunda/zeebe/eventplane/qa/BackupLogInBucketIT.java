/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.data.primarystorage.backup.Filesystem;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.eventplane.backup.BackupLog;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A backup keeps its snapshot and its log in the Event Plane's bucket instead of copying them into
 * the backup store, and a restore from it gets every partition's snapshot and journal from the
 * bucket, up to the checkpoint.
 */
@ZeebeIntegration
final class BackupLogInBucketIT {

  private static final long BACKUP_ID = 7;
  private static final String CLUSTER = "backup-log-it";
  private static final String PROCESS = "event-plane-backup";
  private static final String JOB_TYPE = "event-plane-backup-job";
  private static final int PARTITIONS = 2;

  @TempDir private Path temp;

  @Test
  void shouldRestoreEveryPartitionToTheCheckpointFromTheBucket() throws Exception {
    // given a backup that keeps its log in the bucket, and instances on both sides of it
    final var bucket = temp.resolve("bucket");
    final var store = new FileSystemStore(bucket);
    final List<Long> before;
    final Map<Integer, Long> checkpoints;
    try (final var broker =
        broker(bucket).start().awaitCompleteTopology(1, PARTITIONS, 1, Duration.ofSeconds(60))) {
      deployProcess(broker);
      before = createInstances(broker, 10);
      checkpoints = takeBackup(broker);
      createInstances(broker, 10);
    }

    // then the backup store holds the backups' records, but no snapshot file and no journal segment
    final var backupFiles = new ArrayList<String>();
    try (final var files = Files.walk(temp.resolve("backups"))) {
      files
          .filter(Files::isRegularFile)
          .map(file -> temp.resolve("backups").relativize(file).toString())
          .forEach(backupFiles::add);
    }
    assertThat(backupFiles)
        .describedAs("the backup store's files")
        .isNotEmpty()
        .allMatch(file -> file.endsWith(".json"));
    // and the bucket has each partition's snapshot, and says what its backup needs
    for (int partition = 1; partition <= PARTITIONS; partition++) {
      final var backupLog = backupLog(store, partition);
      assertThat(backupLog.checkpointPosition()).isEqualTo(checkpoints.get(partition));
      assertThat(backupLog.firstIndex()).isLessThanOrEqualTo(backupLog.lastIndex());
      assertThat(backupLog.snapshotId()).isNotNull();
      assertThat(
              SnapshotArchive.descriptor(
                  store, new LogId(CLUSTER, "default", partition), backupLog.snapshotId()))
          .describedAs("the snapshot of partition %d's backup in the bucket", partition)
          .isPresent();
    }
    assertThat(before)
        .describedAs("instances on every partition")
        .extracting(Protocol::decodePartitionId)
        .contains(1, 2);

    // when the cluster is restored from the backup
    final var restoredHistory = UUID.randomUUID();
    deleteContents(temp.resolve("data"));
    try (final var restore =
        new TestRestoreApp()
            .withUnifiedConfig(this::configure)
            .withProperty(EventPlaneBrokers.PREFIX + "enabled", true)
            .withProperty(EventPlaneBrokers.PREFIX + "store.type", "filesystem")
            .withProperty(EventPlaneBrokers.PREFIX + "store.path", bucket.toString())
            .withProperty(EventPlaneBrokers.PREFIX + "backupLog", true)
            .withProperty(EventPlaneBrokers.PREFIX + "restoreHistory", restoredHistory.toString())
            .withBackupId(BACKUP_ID)) {
      restore.start();
    }

    // then every partition holds what it held at the checkpoint, and nothing after it
    try (final var broker =
        broker(bucket).start().awaitCompleteTopology(1, PARTITIONS, 1, Duration.ofSeconds(60))) {
      assertThat(activateAllJobs(broker, before.size()))
          .extracting(ActivatedJob::getProcessInstanceKey)
          .containsExactlyInAnyOrderElementsOf(before);
      // and goes on publishing, in the new history
      for (int partition = 1; partition <= PARTITIONS; partition++) {
        final var log = new LogId(CLUSTER, "default", partition);
        await("the restored history's part of partition " + partition)
            .timeout(Duration.ofSeconds(30))
            .until(
                () -> store.get(ObjectNames.historyPart(log, restoredHistory)),
                content -> content.isPresent());
      }
    }
  }

  private TestStandaloneBroker broker(final Path bucket) {
    return EventPlaneBrokers.withEventPlane(new TestStandaloneBroker(), bucket)
        .withProperty(EventPlaneBrokers.PREFIX + "backupLog", true)
        .withRecordingExporter(false)
        .withSecondaryStorageType(SecondaryStorageType.none)
        .withUnifiedConfig(this::configure);
  }

  private void configure(final Camunda config) {
    // a generated cluster id doesn't survive a restore that recreates the topology
    config.getCluster().setClusterId(CLUSTER);
    config.getCluster().setPartitionCount(PARTITIONS);
    config.getData().getPrimaryStorage().setDirectory(temp.resolve("data").toString());
    final var filesystem = new Filesystem();
    filesystem.setBasePath(temp.resolve("backups").toString());
    config.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.FILESYSTEM);
    config.getData().getPrimaryStorage().getBackup().setFilesystem(filesystem);
    config.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
  }

  /**
   * Takes a backup after a snapshot of every partition that holds an upload floor, and returns each
   * partition's checkpoint position.
   */
  private static Map<Integer, Long> takeBackup(final TestStandaloneBroker broker) {
    final var partitions = PartitionsActuator.of(broker);
    await("a snapshot with the floor on every partition")
        .timeout(Duration.ofSeconds(60))
        .until(
            () -> {
              partitions.takeSnapshot();
              return partitions.query();
            },
            statuses ->
                statuses.size() == PARTITIONS
                    && statuses.values().stream()
                        .allMatch(
                            status ->
                                status.exportedPositionInSnapshot() != null
                                    && status.exportedPositionInSnapshot() > 0));
    final var backups = BackupActuator.of(broker);
    backups.take(BACKUP_ID);
    await("the backup")
        .timeout(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(backups.status(BACKUP_ID).getState()).isEqualTo(StateCode.COMPLETED));
    final var checkpoints = new HashMap<Integer, Long>();
    for (final var detail : backups.status(BACKUP_ID).getDetails()) {
      checkpoints.put(detail.getPartitionId(), detail.getCheckpointPosition());
    }
    assertThat(checkpoints).hasSize(PARTITIONS);
    return checkpoints;
  }

  private static BackupLog backupLog(final FileSystemStore store, final int partition)
      throws IOException {
    final var key = ObjectNames.backupLog(new LogId(CLUSTER, "default", partition), BACKUP_ID);
    return BackupLog.fromJson(
        store
            .get(key)
            .orElseThrow(() -> new AssertionError("Expected the backup log '%s'".formatted(key))));
  }

  private static void deployProcess(final TestStandaloneBroker broker) {
    try (final var client = broker.newClientBuilder().build()) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(PROCESS)
                  .startEvent()
                  .serviceTask("task", task -> task.zeebeJobType(JOB_TYPE))
                  .endEvent()
                  .done(),
              PROCESS + ".bpmn")
          .send()
          .join();
    }
  }

  private static List<Long> createInstances(final TestStandaloneBroker broker, final int count) {
    final var keys = new ArrayList<Long>();
    try (final var client = broker.newClientBuilder().build()) {
      for (int i = 0; i < count; i++) {
        keys.add(
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(PROCESS)
                .latestVersion()
                .send()
                .join()
                .getProcessInstanceKey());
      }
    }
    return keys;
  }

  /** Activates jobs until there are as many as expected, then once more, which finds none more. */
  private static List<ActivatedJob> activateAllJobs(
      final TestStandaloneBroker broker, final int expected) {
    final var jobs = new ArrayList<ActivatedJob>();
    try (final var client = broker.newClientBuilder().build()) {
      await("every instance's job")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                jobs.addAll(activate(client, expected));
                assertThat(jobs).hasSizeGreaterThanOrEqualTo(expected);
              });
      jobs.addAll(activate(client, expected));
    }
    return jobs;
  }

  private static List<ActivatedJob> activate(final CamundaClient client, final int max) {
    return client
        .newActivateJobsCommand()
        .jobType(JOB_TYPE)
        .maxJobsToActivate(max)
        .timeout(Duration.ofMinutes(5))
        .requestTimeout(Duration.ofSeconds(2))
        .send()
        .join()
        .getJobs();
  }

  private static void deleteContents(final Path directory) throws IOException {
    try (final var paths = Files.walk(directory)) {
      paths
          .sorted(Comparator.reverseOrder())
          .filter(path -> !path.equals(directory))
          .forEach(path -> path.toFile().delete());
    }
  }
}
