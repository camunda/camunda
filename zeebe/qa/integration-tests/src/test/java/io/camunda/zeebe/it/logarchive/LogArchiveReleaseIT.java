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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry.Type;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreException;
import io.camunda.zeebe.restore.logarchive.LogArchiveView;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * With a short retention window, the leader releases the log below the restore floor: restoring to
 * a checkpoint below the floor is refused, restoring to one above it works.
 */
@ZeebeIntegration
final class LogArchiveReleaseIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveReleaseIT.class);
  private static final int PARTITION = 1;
  private static final int INSTANCES = 30;
  private static final Duration WINDOW = Duration.ofSeconds(5);
  private static final Duration TIMEOUT = Duration.ofSeconds(90);
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final Path restoredData = testDirectory.resolve("restored-data").toAbsolutePath();
  private final LogArchiveWorkload workload = new LogArchiveWorkload();

  @TestZeebe
  private final TestStandaloneBroker source =
      broker(testDirectory.resolve("source").toAbsolutePath());

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker restored = broker(restoredData);

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    bucket = new LogArchiveBucket(basePath);
  }

  @AfterEach
  void tearDown() {
    bucket.close();
  }

  @Test
  void shouldReleaseBelowTheFloorAndRefuseRestoresBelowIt() {
    // given -- a checkpoint with an archived snapshot below it, then a long run of log and a second
    // snapshot and checkpoint, so that the first checkpoint falls out of the retention window
    final var partitions = PartitionsActuator.of(source);
    final long oldCheckpoint;
    final long newCheckpoint;
    try (final var client = source.newClientBuilder().build()) {
      final long process = workload.deployProcess(client);
      LogArchiveWorkload.createInstances(client, process, "old", INSTANCES);
      partitions.takeSnapshot();
      awaitArchivedSnapshots(1);
      oldCheckpoint = LogArchiveWorkload.takeBackup(source);

      LogArchiveWorkload.createInstances(client, process, "mid", INSTANCES);
      LogArchiveWorkload.createInstances(client, process, "mid", INSTANCES);
      partitions.takeSnapshot();
      awaitArchivedSnapshots(2);
      LogArchiveWorkload.createInstances(client, process, "mid", INSTANCES);
      newCheckpoint = LogArchiveWorkload.takeBackup(source);
      LogArchiveWorkload.createInstances(client, process, "after", INSTANCES);
    }

    // when -- the window passes and the releaser runs: it releases up to the floor of the newest
    // checkpoint, which lies above the snapshot of the old one
    await("the log is released beyond the snapshot of the old checkpoint")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              assertThat(view.findCheckpoint(newCheckpoint)).isPresent();
              assertThat(view.findCheckpoint(oldCheckpoint)).isPresent();
              assertThat(view.hasReleased()).isTrue();
              assertThat(view.releasedIndex())
                  .isGreaterThanOrEqualTo(floorIndex(view, oldCheckpoint));
            });
    final var released = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
    final long floorIndex = floorIndex(released, newCheckpoint);
    source.stop();

    // then -- LOG_RELEASED is in the archive, below the floor snapshot, and merged objects at or
    // below it are no longer the cluster's
    final var archive = bucket.readAndVerify(TIMELINE, PARTITION);
    LOG.info("Verified the released log archive: {}", archive.summary());
    final var entries =
        archive.commits().stream().filter(c -> c.type() == Type.LOG_RELEASED).toList();
    assertThat(entries).isNotEmpty();
    assertThat(entries.getLast().releasedIndex()).isNotNull().isLessThan(floorIndex);
    assertThat(archive.releasedIndex()).isLessThan(floorIndex);
    assertThat(archive.view().mergedObjects())
        .allMatch(o -> o.lastIndex() > archive.releasedIndex());

    // and -- a restore below the floor is refused before anything is restored
    assertThatThrownBy(
            () -> LogArchiveWorkload.restore(bucket.store(), TIMELINE, oldCheckpoint, restoredData))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("below the restore floor");

    // and -- a restore above it works and holds exactly the state at that checkpoint
    LogArchiveWorkload.restore(bucket.store(), TIMELINE, newCheckpoint, restoredData);
    restored.start().awaitCompleteTopology();
    try (final var client = restored.newClientBuilder().build()) {
      final var activated = new ArrayList<ActivatedJob>();
      await("all jobs created before the checkpoint are activated")
          .atMost(TIMEOUT)
          .pollInterval(Duration.ofMillis(500))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                activated.addAll(workload.activateJobs(client));
                assertThat(activated).hasSize(4 * INSTANCES);
              });
      assertThat(activated)
          .extracting(job -> job.getVariablesAsMap().get("phase"))
          .doesNotContain("after");
    }
  }

  /** The index of the snapshot a restore to the checkpoint starts from. */
  private static long floorIndex(final LogArchiveView view, final long checkpointId) {
    final long position = view.findCheckpoint(checkpointId).orElseThrow().checkpointPosition();
    return view.snapshots().stream()
        .filter(s -> s.processedPosition() < position)
        .filter(s -> s.lastFollowupEventPosition() < position)
        .filter(s -> s.maxExportedPosition() < position)
        .mapToLong(s -> s.index())
        .max()
        .orElseThrow();
  }

  private void awaitArchivedSnapshots(final int count) {
    await("%d snapshots are archived".formatted(count))
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(bucket.tryView(TIMELINE, PARTITION))
                    .hasValueSatisfying(
                        view -> assertThat(view.snapshots()).hasSizeGreaterThanOrEqualTo(count)));
  }

  private TestStandaloneBroker broker(final Path dataDirectory) {
    return LogArchiveWorkload.enableMerging(
            LogArchiveBucket.enableLogArchive(
                new TestStandaloneBroker()
                    .withUnifiedConfig(
                        cfg -> {
                          LogArchiveBucket.configureStore(cfg, basePath);
                          cfg.getData().getPrimaryStorage().setDirectory(dataDirectory.toString());
                        })),
            2)
        .withProperty("zeebe.broker.data.backup.logArchive.retentionWindow", WINDOW.toString())
        .withProperty("zeebe.broker.data.backup.logArchive.releaseInterval", "PT1S");
  }
}
