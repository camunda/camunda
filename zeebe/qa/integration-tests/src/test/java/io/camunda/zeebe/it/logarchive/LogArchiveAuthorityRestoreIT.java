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
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreException;
import io.camunda.zeebe.restore.logarchive.LogArchiveView;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cluster in mode B archives, then a test helper plays the authority (spec section 25.3): it
 * compacts archived objects into a multi-frame object, removes a prefix below the restore hold and
 * deletes the objects it replaced. A restore reads the result, refuses points below the hold and
 * rejects invalid maintenance entries by name.
 */
@ZeebeIntegration
final class LogArchiveAuthorityRestoreIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveAuthorityRestoreIT.class);
  private static final int PARTITION = 1;
  private static final int INSTANCES = 30;
  private static final Duration WINDOW = Duration.ofSeconds(5);
  private static final Duration TIMEOUT = Duration.ofSeconds(120);
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final Path restoredData = testDirectory.resolve("restored-data").toAbsolutePath();
  private final LogArchiveWorkload workload = new LogArchiveWorkload();

  @TestZeebe
  private final TestStandaloneBroker source =
      LogArchiveWorkload.enableMerging(
              LogArchiveBucket.enableLogArchive(
                  new TestStandaloneBroker()
                      .withUnifiedConfig(
                          cfg -> {
                            LogArchiveBucket.configureStore(cfg, basePath);
                            cfg.getData()
                                .getPrimaryStorage()
                                .setDirectory(
                                    testDirectory.resolve("source").toAbsolutePath().toString());
                          })),
              2)
          .withProperty("zeebe.broker.data.backup.logArchive.compactionMode", "AUTHORITY")
          .withProperty("zeebe.broker.data.backup.logArchive.retentionWindow", WINDOW.toString())
          .withProperty("zeebe.broker.data.backup.logArchive.releaseInterval", "PT1S");

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker restored =
      LogArchiveBucket.enableLogArchive(
          new TestStandaloneBroker()
              .withUnifiedConfig(
                  cfg -> {
                    LogArchiveBucket.configureStore(cfg, basePath);
                    cfg.getData().getPrimaryStorage().setDirectory(restoredData.toString());
                  }));

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
  void shouldRestoreFromObjectsTheAuthorityCompactedAndRejectInvalidEntries() throws IOException {
    // given -- an archive in mode B with a hold above an old checkpoint's snapshot
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
    await("the hold moved to the snapshot of the new checkpoint")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              assertThat(view.findCheckpoint(newCheckpoint)).isPresent();
              assertThat(view.findCheckpoint(oldCheckpoint)).isPresent();
              assertThat(view.holdIndex()).isEqualTo(floorIndex(view, newCheckpoint));
            });
    source.stop();

    final var archive = bucket.readAndVerify(TIMELINE, PARTITION);
    LOG.info("Verified the mode B log archive: {}", archive.summary());
    final var view = archive.view();
    final long hold = view.holdIndex();
    final var objects = LogArchiveAuthority.objects(view);
    final var below = objects.stream().filter(o -> o.lastIndex() < hold).toList();
    assertThat(below).as("objects below the hold %d", hold).hasSizeGreaterThanOrEqualTo(3);

    // when -- the authority compacts the two oldest objects, compacts the next ones up to the
    // object across the hold, and removes the first compacted object, which lies below the hold
    final var authority =
        new LogArchiveAuthority(
            bucket.store(), bucket.partitionDirectory(TIMELINE, PARTITION), TIMELINE, PARTITION);
    final long base = view.lastSeq();
    final var first = authority.compact(1, base, objects.subList(0, 2));
    final int acrossHold =
        objects.indexOf(
            objects.stream().filter(o -> o.lastIndex() >= hold).findFirst().orElseThrow());
    final var second = authority.compact(2, base, objects.subList(2, acrossHold + 1));
    authority.remove(3, base, List.of(first));
    LOG.info(
        "Compacted {} and {}, removed up to index {}",
        first.key(),
        second.key(),
        first.lastIndex());

    // then -- a restore below the hold is refused
    assertThatThrownBy(
            () -> LogArchiveWorkload.restore(bucket.store(), TIMELINE, oldCheckpoint, restoredData))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("below the restore hold");

    // and -- a removal at or above the hold is refused, naming the entry
    final var current = currentObjects(second, objects, acrossHold);
    final var crossing = current.getFirst();
    assertThat(crossing.lastIndex()).isGreaterThanOrEqualTo(hold);
    authority.removeEntry(4, base, crossing.lastIndex(), crossing.lastPosition());
    assertThatThrownBy(
            () -> LogArchiveWorkload.restore(bucket.store(), TIMELINE, newCheckpoint, restoredData))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("maintenance entry 4")
        .hasMessageContaining("restore hold");
    authority.deleteEntry(4);

    // and -- a compacted range that is not on object boundaries is refused, naming the entry
    final var wide =
        current.stream().filter(o -> o.lastIndex() > o.firstIndex()).findFirst().orElseThrow();
    authority.compactedEntry(
        4,
        base,
        new LogObjectJson(
            "data/bogus-c4.seg",
            wide.term(),
            wide.firstIndex() + 1,
            wide.lastIndex(),
            wide.lastPosition(),
            wide.lastPosition(),
            (int) (wide.lastIndex() - wide.firstIndex()),
            1,
            "0".repeat(64)));
    assertThatThrownBy(
            () -> LogArchiveWorkload.restore(bucket.store(), TIMELINE, newCheckpoint, restoredData))
        .isInstanceOf(LogArchiveRestoreException.class)
        .hasMessageContaining("maintenance entry 4")
        .hasMessageContaining("boundaries");
    authority.deleteEntry(4);

    // and -- a restore to the new checkpoint reads the compacted objects and holds exactly the
    // state at that checkpoint
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

  /** The objects after the removal: the second compacted object, then the untouched rest. */
  private static List<LogObjectJson> currentObjects(
      final LogObjectJson second, final List<LogObjectJson> objects, final int acrossHold) {
    final var current = new ArrayList<LogObjectJson>();
    current.add(second);
    current.addAll(objects.subList(acrossHold + 1, objects.size()));
    return current;
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
}
