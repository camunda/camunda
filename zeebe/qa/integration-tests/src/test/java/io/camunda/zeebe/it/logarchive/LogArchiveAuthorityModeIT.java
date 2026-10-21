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
import io.camunda.zeebe.backup.logarchive.model.CommitEntry.Type;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cluster in mode B (spec section 25.2): the timeline says so, nothing is merged or released, the
 * restore hold moves up to archived snapshots, and the cluster deletes no archived object, while
 * its journal still compacts.
 */
@ZeebeIntegration
final class LogArchiveAuthorityModeIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveAuthorityModeIT.class);
  private static final int PARTITION = 1;
  private static final int INSTANCES = 60;
  private static final Duration WINDOW = Duration.ofSeconds(5);
  private static final Duration TIMEOUT = Duration.ofSeconds(120);
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final Path sourceData = testDirectory.resolve("source").toAbsolutePath();
  private final LogArchiveWorkload workload = new LogArchiveWorkload();

  @TestZeebe
  private final TestStandaloneBroker source =
      LogArchiveWorkload.enableMerging(
              LogArchiveBucket.enableLogArchive(
                  new TestStandaloneBroker()
                      .withUnifiedConfig(
                          cfg -> {
                            LogArchiveBucket.configureStore(cfg, basePath);
                            cfg.getData().getPrimaryStorage().setDirectory(sourceData.toString());
                          })),
              2)
          .withProperty("zeebe.broker.data.backup.logArchive.compactionMode", "AUTHORITY")
          .withProperty("zeebe.broker.data.backup.logArchive.retentionWindow", WINDOW.toString())
          .withProperty("zeebe.broker.data.backup.logArchive.releaseInterval", "PT1S");

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
  void shouldMoveTheHoldAndDeleteNothingWhileTheJournalCompacts() throws IOException {
    // given -- a snapshot and a backup, then more log and a second snapshot and backup, so that
    // the first backup falls out of the retention window
    final var partitions = PartitionsActuator.of(source);
    try (final var client = source.newClientBuilder().build()) {
      final long process = workload.deployProcess(client);
      createLargeInstances(client, process, INSTANCES);
      partitions.takeSnapshot();
      awaitArchivedSnapshots(1);
      LogArchiveWorkload.takeBackup(source);

      createLargeInstances(client, process, INSTANCES);
      partitions.takeSnapshot();
      awaitArchivedSnapshots(2);
      createLargeInstances(client, process, INSTANCES);
      LogArchiveWorkload.takeBackup(source);
      createLargeInstances(client, process, INSTANCES);
    }

    // when -- the hold mover runs, and the journal compacts up to the snapshots
    await("the hold moved to an archived snapshot")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              assertThat(view.isAuthority()).isTrue();
              assertThat(view.hasMovedHold()).isTrue();
              assertThat(view.snapshots()).anyMatch(s -> s.index() == view.holdIndex());
            });
    awaitJournalCompaction();
    source.stop();

    // then -- the timeline says AUTHORITY, nothing is merged or released, and the hold only moved
    // up, always to the index of an archived snapshot
    final var archive = bucket.readAndVerify(TIMELINE, PARTITION);
    LOG.info("Verified the mode B log archive: {}", archive.summary());
    assertThat(archive.commits().getFirst().isAuthority()).isTrue();
    assertThat(archive.commits())
        .extracting(CommitEntry::type)
        .doesNotContain(Type.LOG_MERGED, Type.LOG_RELEASED)
        .contains(Type.HOLD_MOVED);
    final var holds =
        archive.commits().stream()
            .filter(c -> c.type() == Type.HOLD_MOVED)
            .map(CommitEntry::holdIndex)
            .toList();
    assertThat(holds).isSorted().doesNotHaveDuplicates();
    assertThat(archive.view().holdIndex()).isEqualTo(holds.getLast());
    assertThat(archive.view().holdIndex()).isGreaterThan(archive.startIndex());
    assertThat(archive.mergedObjects()).isEmpty();
    assertThat(archive.view().hasReleased()).isFalse();

    // and -- the cluster deleted nothing: every archived object is still there, and nothing else
    final var prefix = LogArchiveWorkload.dataKeyPrefix(archive.view());
    assertThat(archive.dataKeys())
        .containsExactlyInAnyOrderElementsOf(
            archive.objects().stream().map(o -> prefix + o.key()).toList());
    assertThat(archive.objects().getFirst().firstIndex()).isEqualTo(archive.startIndex());
  }

  /** Large variables, so that the journal rolls over its small segments. */
  private static void createLargeInstances(
      final CamundaClient client, final long process, final int count) {
    for (int i = 0; i < count; i++) {
      client
          .newCreateInstanceCommand()
          .processDefinitionKey(process)
          .variables(Map.of("phase", "load", "payload", "x".repeat(16 * 1024)))
          .send()
          .join();
    }
  }

  /** Continuous backups pin the log until archived, so the first segment goes once F passed it. */
  private void awaitJournalCompaction() {
    final var partitions = PartitionsActuator.of(source);
    final var partitionDirectory = sourceData.resolve("default/partitions/" + PARTITION);
    await("the first segment of the journal is compacted")
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(
            () -> {
              partitions.takeSnapshot();
              try (final Stream<Path> files = Files.list(partitionDirectory)) {
                return files.noneMatch(f -> f.getFileName().toString().endsWith("-1.log"));
              }
            });
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
