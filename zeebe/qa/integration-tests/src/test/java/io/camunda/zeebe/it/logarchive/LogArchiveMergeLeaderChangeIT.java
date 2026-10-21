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

import io.atomix.cluster.MemberId;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
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
 * When the leader goes away while merging, the new leader carries on: the objects the archive
 * currently consists of stay contiguous without gaps or overlaps, an object nobody references is
 * deleted, and a restore still works.
 */
@ZeebeIntegration
final class LogArchiveMergeLeaderChangeIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveMergeLeaderChangeIT.class);
  private static final int PARTITION = 1;
  private static final int BROKERS = 3;
  private static final int BEFORE_CHECKPOINT = 60;
  private static final long ORPHAN_TERM = 9999;
  private static final Duration TIMEOUT = Duration.ofSeconds(90);
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final Path restoredData = testDirectory.resolve("restored-data").toAbsolutePath();
  private final LogArchiveWorkload workload = new LogArchiveWorkload();

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKERS)
          .withPartitionsCount(1)
          .withReplicationFactor(BROKERS)
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              (id, broker) ->
                  LogArchiveWorkload.enableMerging(
                      LogArchiveBucket.enableLogArchive(
                          broker.withUnifiedConfig(
                              cfg -> {
                                LogArchiveBucket.configureStore(cfg, basePath);
                                cfg.getData()
                                    .getPrimaryStorage()
                                    .setDirectory(dataDirectory(id).toString());
                              })),
                      2))
          .build();

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker restored =
      LogArchiveWorkload.enableMerging(
          LogArchiveBucket.enableLogArchive(
              new TestStandaloneBroker()
                  .withUnifiedConfig(
                      cfg -> {
                        LogArchiveBucket.configureStore(cfg, basePath);
                        cfg.getData().getPrimaryStorage().setDirectory(restoredData.toString());
                      })),
          2);

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    cluster.start().awaitCompleteTopology();
    bucket = new LogArchiveBucket(basePath);
  }

  @AfterEach
  void tearDown() {
    bucket.close();
  }

  @Test
  void shouldKeepMergedRangesContiguousAndDeleteOrphansAfterALeaderChange() {
    // given -- merges happen constantly, and a checkpoint exists
    final long process;
    final long checkpointId;
    try (final var client = cluster.newClientBuilder().build()) {
      process = workload.deployProcess(client);
      LogArchiveWorkload.createInstances(client, process, "before", BEFORE_CHECKPOINT);
    }
    checkpointId = LogArchiveWorkload.takeBackup(cluster.brokers().values().iterator().next());
    await("the leader merged and the checkpoint is archived")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              assertThat(view.findCheckpoint(checkpointId)).isPresent();
              assertThat(view.mergedObjects()).hasSizeGreaterThanOrEqualTo(2);
              assertThat(view.mergedIndex()).isGreaterThan(view.startIndex() + 1);
            });
    final var oldLeader = cluster.leaderForPartition(PARTITION);
    final long oldTerm = maxTerm();

    // when -- the leader stops in the middle of load that keeps it merging, and an object of a
    // leader that never committed it is left behind
    final long mergedAtFailover;
    final String orphanKey;
    try (final var load =
        new LogArchiveWorkload.Load(() -> cluster.newClientBuilder().build(), process, "after")) {
      oldLeader.stop();
      final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
      orphanKey = LogArchiveKeys.dataKey(view.startIndex(), view.startIndex() + 1, ORPHAN_TERM);
      bucket
          .store()
          .put(
              LogArchiveKeys.partitionPrefix(TIMELINE, PARTITION) + orphanKey, new byte[] {1, 2, 3})
          .join();
      await("a new leader is elected")
          .atMost(TIMEOUT)
          .ignoreExceptions()
          .until(() -> cluster.leaderForPartition(PARTITION), leader -> leader != oldLeader);

      // then -- the new leader merges in a higher term and deletes the orphan
      await("the new leader merges")
          .atMost(TIMEOUT)
          .untilAsserted(
              () -> {
                final var current = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
                assertThat(current.mergedObjects())
                    .as("merged objects of a term above the old leader's")
                    .anyMatch(o -> o.term() > oldTerm);
              });
      mergedAtFailover = bucket.tryView(TIMELINE, PARTITION).orElseThrow().mergedIndex();
      await("every orphan at or below M is deleted")
          .atMost(TIMEOUT)
          // the filesystem store lists its temporary files too, which brokers keep replacing
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertThat(
                          LogArchiveWorkload.orphans(
                              bucket.store(), TIMELINE, PARTITION, mergedAtFailover))
                      .isEmpty());
    }
    assertThat(
            bucket
                .store()
                .getIfExists(LogArchiveKeys.partitionPrefix(TIMELINE, PARTITION) + orphanKey)
                .join())
        .as("the planted orphan is deleted")
        .isEmpty();

    // and -- the objects the archive consists of are contiguous, whoever merged them
    cluster.shutdown();
    final var archive = bucket.readAndVerify(TIMELINE, PARTITION);
    LOG.info("Verified the log archive: {}", archive.summary());
    final var merged = archive.mergedObjects();
    assertThat(merged).extracting(o -> o.term()).contains(oldTerm).anyMatch(t -> t > oldTerm);
    assertThat(merged.getFirst().firstIndex()).isEqualTo(archive.startIndex());
    for (int i = 1; i < merged.size(); i++) {
      assertThat(merged.get(i).firstIndex())
          .as("merged object %s follows %s", merged.get(i).key(), merged.get(i - 1).key())
          .isEqualTo(merged.get(i - 1).lastIndex() + 1);
    }
    assertThat(archive.mergedIndex()).isGreaterThanOrEqualTo(mergedAtFailover);

    // and -- a restore to the checkpoint taken before the leader change works
    LogArchiveWorkload.restore(bucket.store(), TIMELINE, checkpointId, restoredData);
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
                assertThat(activated).hasSize(BEFORE_CHECKPOINT);
              });
      assertThat(activated)
          .extracting(job -> job.getVariablesAsMap().get("phase"))
          .containsOnly("before");
    }
  }

  private long maxTerm() {
    return bucket.tryView(TIMELINE, PARTITION).orElseThrow().mergedObjects().stream()
        .mapToLong(o -> o.term())
        .max()
        .orElseThrow();
  }

  private Path dataDirectory(final MemberId brokerId) {
    return testDirectory.resolve("broker-" + brokerId.id()).toAbsolutePath();
  }
}
