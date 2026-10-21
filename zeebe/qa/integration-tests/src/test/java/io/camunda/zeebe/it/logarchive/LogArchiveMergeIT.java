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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry.Type;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The leader replaces the small objects it cuts with merged objects, deletes the replaced ones
 * after the grace period, and a restore reads the merged objects instead.
 */
@ZeebeIntegration
final class LogArchiveMergeIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveMergeIT.class);
  private static final int PARTITION = 1;
  private static final int BEFORE_SNAPSHOT = 30;
  private static final int BEFORE_CHECKPOINT = 30;
  private static final int AFTER_CHECKPOINT = 20;
  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final Path restoredData = testDirectory.resolve("restored-data").toAbsolutePath();
  private final LogArchiveWorkload workload = new LogArchiveWorkload();

  @TestZeebe private final TestStandaloneBroker source = broker(testDirectory.resolve("source"));

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
  void shouldReplaceSmallObjectsWithMergedOnesAndRestoreFromThem() {
    // given -- instances before a snapshot, more before a checkpoint, and more after it
    final var partitions = PartitionsActuator.of(source);
    final long checkpointId;
    try (final var client = source.newClientBuilder().build()) {
      final long process = workload.deployProcess(client);
      LogArchiveWorkload.createInstances(client, process, "before", BEFORE_SNAPSHOT);
      partitions.takeSnapshot();
      LogArchiveWorkload.createInstances(client, process, "before", BEFORE_CHECKPOINT);
      checkpointId = LogArchiveWorkload.takeBackup(source);
      LogArchiveWorkload.createInstances(client, process, "after", AFTER_CHECKPOINT);
    }

    // when -- the leader merges, and the cleaner deletes what the merges replaced
    final var deletedUpTo = new AtomicLong();
    await("F covers the checkpoint, merged objects replace small ones, which are deleted")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              final var checkpoint = view.findCheckpoint(checkpointId);
              assertThat(checkpoint).isPresent();
              assertThat(view.archivedPosition())
                  .isGreaterThanOrEqualTo(checkpoint.get().checkpointPosition());
              assertThat(view.snapshots())
                  .anySatisfy(
                      s ->
                          assertThat(s.processedPosition())
                              .isLessThan(checkpoint.get().checkpointPosition()));
              assertThat(view.mergedObjects()).hasSizeGreaterThanOrEqualTo(2);
              final var replaced = LogArchiveWorkload.replacedObjects(view);
              assertThat(replaced).isNotEmpty();
              assertThat(replaced)
                  .as("replaced objects are deleted")
                  .noneMatch(o -> LogArchiveWorkload.exists(bucket.store(), view, o.key()));
              deletedUpTo.set(view.mergedIndex());
            });
    source.stop();

    // then -- merged objects cover contiguous ranges from the start index, and every object they
    // replaced is gone
    final var archive = bucket.readAndVerify(TIMELINE, PARTITION);
    LOG.info("Verified the merged log archive: {}", archive.summary());
    assertThat(archive.commits()).extracting(c -> c.type()).contains(Type.LOG_MERGED);
    final var merged = archive.mergedObjects();
    assertThat(merged.getFirst().firstIndex()).isEqualTo(archive.startIndex());
    for (int i = 1; i < merged.size(); i++) {
      assertThat(merged.get(i).firstIndex()).isEqualTo(merged.get(i - 1).lastIndex() + 1);
    }
    assertThat(archive.mergedIndex()).isEqualTo(merged.getLast().lastIndex());
    assertThat(merged)
        .as("some merged object replaced several small objects")
        .anyMatch(
            m ->
                archive.objects().stream()
                        .filter(
                            o -> o.firstIndex() >= m.firstIndex() && o.lastIndex() <= m.lastIndex())
                        .count()
                    > 1);
    final var replaced =
        LogArchiveWorkload.replacedObjects(archive.view()).stream()
            .filter(o -> o.lastIndex() <= deletedUpTo.get())
            .toList();
    assertThat(replaced).isNotEmpty();
    replaced.forEach(
        o ->
            assertThat(LogArchiveWorkload.exists(bucket.store(), archive.view(), o.key()))
                .as("replaced object %s is deleted", o.key())
                .isFalse());
    assertThat(archive.orphanKeys())
        .as("no object nobody references at or below M")
        .noneMatch(k -> LogArchiveWorkload.lastIndexOf(k) <= deletedUpTo.get());

    // and -- a restore uses the merged objects, as the replaced ones are gone, and the restored
    // partition holds exactly the state at the checkpoint
    final var result =
        LogArchiveWorkload.restore(bucket.store(), TIMELINE, checkpointId, restoredData);
    LOG.info("Restored {}", result.partitions().get(PARTITION));
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
                assertThat(activated).hasSize(BEFORE_SNAPSHOT + BEFORE_CHECKPOINT);
              });
      assertThat(activated)
          .extracting(job -> job.getVariablesAsMap().get("phase"))
          .containsOnly("before");
      assertThat(workload.activateJobs(client)).as("no job after the checkpoint").isEmpty();
    }
  }

  private TestStandaloneBroker broker(final Path dataDirectory) {
    return LogArchiveWorkload.enableMerging(
        LogArchiveBucket.enableLogArchive(
            new TestStandaloneBroker()
                .withUnifiedConfig(
                    cfg -> {
                      LogArchiveBucket.configureStore(cfg, basePath);
                      cfg.getData()
                          .getPrimaryStorage()
                          .setDirectory(dataDirectory.toAbsolutePath().toString());
                    })),
        3);
  }
}
