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

import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.data.primarystorage.backup.Filesystem;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.eventplane.consumer.ConsumerListener;
import io.camunda.zeebe.eventplane.consumer.Cursor;
import io.camunda.zeebe.eventplane.consumer.EventPlaneConsumer;
import io.camunda.zeebe.eventplane.consumer.Start;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.history.ManifestJson;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A restore from a backup starts a new history from the checkpoint, and a group that read past the
 * checkpoint is told where the histories part, then reads on in the new one.
 */
@ZeebeIntegration
final class RestoreIT {

  private static final long BACKUP_ID = 1;

  @TempDir private Path temp;
  private EventPlaneConsumer worker;

  @AfterEach
  void tearDown() {
    if (worker != null) {
      worker.close();
    }
  }

  @Test
  void shouldStartANewHistoryWhereTheBackupEnds() throws Exception {
    // given a broker that took a backup, and then more load
    final var bucket = temp.resolve("bucket");
    final var store = new FileSystemStore(bucket);
    final var changes = new CopyOnWriteArrayList<HistoryChange>();
    final Set<Long> delivered = new ConcurrentSkipListSet<>();
    final long checkpoint;
    try (final var broker = broker(bucket).start().awaitCompleteTopology()) {
      EventPlaneBrokers.publishMessages(broker, 10);
      checkpoint = takeBackup(broker);
      EventPlaneBrokers.publishMessages(broker, 10);
      await("uploads past the checkpoint")
          .timeout(Duration.ofSeconds(30))
          .until(() -> uploadedLastPosition(store) > checkpoint);
      worker = startWorker(store, delivered, changes);
      await("the group reads past the checkpoint")
          .timeout(Duration.ofSeconds(30))
          .until(() -> delivered.contains(uploadedLastPosition(store)));
    }
    final var before = Bucket.logs(store);
    final var oldHistory = Bucket.readLog(store, before.getFirst()).history();

    // when the cluster is restored from the backup, and goes on
    final var restoredHistory = UUID.randomUUID();
    deleteContents(temp.resolve("data"));
    try (final var restore =
        new TestRestoreApp()
            .withUnifiedConfig(this::configure)
            .withProperty(EventPlaneBrokers.PREFIX + "enabled", true)
            .withProperty(EventPlaneBrokers.PREFIX + "store.type", "filesystem")
            .withProperty(EventPlaneBrokers.PREFIX + "store.path", bucket.toString())
            .withProperty(EventPlaneBrokers.PREFIX + "restoreHistory", restoredHistory.toString())
            .withBackupId(BACKUP_ID)) {
      restore.start();
    }
    try (final var broker = broker(bucket).start().awaitCompleteTopology()) {
      EventPlaneBrokers.publishMessages(broker, 10);

      // then
      assertThat(Bucket.logs(store))
          .describedAs("the restored cluster publishes the same logs")
          .isEqualTo(before);
      final var log = before.getFirst();
      final var part =
          await("the restored history's part")
              .timeout(Duration.ofSeconds(30))
              .until(
                  () -> store.get(ObjectNames.historyPart(log, restoredHistory)),
                  content -> content.isPresent());
      final var base = ManifestJson.read(part.orElseThrow()).partitions().get(1);
      assertThat(base.sourceHistoryId()).isEqualTo(oldHistory);
      assertThat(base.position()).isEqualTo(checkpoint);
      await("the group learns where the histories part, and reads on")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                assertThat(changes).hasSize(1);
                assertThat(changes.getFirst().current()).isEqualTo(restoredHistory);
                assertThat(changes.getFirst().shared()).isEqualTo(base);
                assertThat(changes.getFirst().old().position()).isGreaterThan(checkpoint);
                assertThat(delivered).contains(uploadedLastPosition(store));
              });
    }
  }

  private TestStandaloneBroker broker(final Path bucket) {
    return EventPlaneBrokers.withEventPlane(new TestStandaloneBroker(), bucket)
        .withRecordingExporter(false)
        .withSecondaryStorageType(SecondaryStorageType.none)
        .withUnifiedConfig(this::configure);
  }

  private void configure(final Camunda config) {
    // a generated cluster id doesn't survive a restore that recreates the topology
    config.getCluster().setClusterId("restore-it");
    config.getData().getPrimaryStorage().setDirectory(temp.resolve("data").toString());
    final var filesystem = new Filesystem();
    filesystem.setBasePath(temp.resolve("backups").toString());
    config.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.FILESYSTEM);
    config.getData().getPrimaryStorage().getBackup().setFilesystem(filesystem);
    config.getData().getSecondaryStorage().setAutoconfigureCamundaExporter(false);
  }

  /**
   * Takes a backup after a snapshot that holds an upload floor, which names the history the backup
   * is taken in, and returns its checkpoint position. Without exporters, a snapshot's exported
   * position is the floor's, and 0 while the floor is held.
   */
  private static long takeBackup(final TestStandaloneBroker broker) {
    final var partitions = PartitionsActuator.of(broker);
    await("a snapshot with the floor")
        .timeout(Duration.ofSeconds(30))
        .until(
            () -> {
              partitions.takeSnapshot();
              return partitions.query().get(1).exportedPositionInSnapshot();
            },
            exported -> exported != null && exported > 0);
    final var backups = BackupActuator.of(broker);
    backups.take(BACKUP_ID);
    await("the backup")
        .timeout(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(backups.status(BACKUP_ID).getState()).isEqualTo(StateCode.COMPLETED));
    return backups.status(BACKUP_ID).getDetails().getFirst().getCheckpointPosition();
  }

  private static EventPlaneConsumer startWorker(
      final FileSystemStore store, final Set<Long> delivered, final List<HistoryChange> changes) {
    final var worker =
        EventPlaneConsumer.builder()
            .store(store)
            .group("g")
            .workerId("w-a")
            .subscribe("*", "*")
            .start(Start.EARLIEST)
            .handler((log, record) -> delivered.add(record.getPosition()))
            .listener(
                new ConsumerListener() {
                  @Override
                  public void onHistoryChange(
                      final LogId log, final Cursor old, final Base shared, final UUID current) {
                    changes.add(new HistoryChange(old, shared, current));
                  }
                })
            .heartbeatInterval(Duration.ofMillis(200))
            .lease(Duration.ofSeconds(2))
            .cursorInterval(Duration.ofMillis(500))
            .listingInterval(Duration.ofMillis(500))
            .build();
    worker.start();
    return worker;
  }

  private static long uploadedLastPosition(final FileSystemStore store) throws Exception {
    final var logs = Bucket.logs(store);
    return logs.isEmpty() ? -1 : Bucket.readLog(store, logs.getFirst()).lastPosition();
  }

  private static void deleteContents(final Path directory) throws Exception {
    try (final var paths = Files.walk(directory)) {
      paths
          .sorted(java.util.Comparator.reverseOrder())
          .filter(path -> !path.equals(directory))
          .forEach(path -> path.toFile().delete());
    }
  }

  private record HistoryChange(Cursor old, Base shared, UUID current) {}
}
