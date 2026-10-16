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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single broker with the log archive enabled archives its committed log continuously into a
 * filesystem bucket that describes itself, and never compacts log that is not archived yet.
 */
@ZeebeIntegration
final class LogArchiveProgressIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveProgressIT.class);
  private static final int PARTITION = 1;
  private static @TempDir Path tempDir;

  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());
  private final String jobType = "job-" + UUID.randomUUID();

  @TestZeebe
  private final TestStandaloneBroker broker =
      LogArchiveBucket.enableLogArchive(
          new TestStandaloneBroker()
              .withUnifiedConfig(cfg -> LogArchiveBucket.configureStore(cfg, basePath)));

  private LogArchiveBucket bucket;
  private PartitionsActuator partitions;

  @BeforeEach
  void setUp() {
    bucket = new LogArchiveBucket(basePath);
    partitions = PartitionsActuator.of(broker);
  }

  @AfterEach
  void tearDown() throws IOException {
    // a test may have made the data directory read-only; the temp dir cleanup needs it writable
    final var dataDirectory = bucket.partitionDirectory(TIMELINE, PARTITION).resolve("data");
    if (Files.isDirectory(dataDirectory)) {
      Files.setPosixFilePermissions(dataDirectory, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
    bucket.close();
  }

  @Test
  void shouldArchiveCommittedLogContinuously() {
    // given
    try (final var client = broker.newClientBuilder().build()) {
      final long process = deployProcess(client);

      // when
      createInstances(client, process, 200);
    }
    final long processedPosition = processedPosition();
    partitions.takeSnapshot();

    // then -- F follows the log, and the bucket describes snapshots, sealed chunks and roots
    await("F reaches the processed position, a snapshot is archived and a chunk sealed")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION);
              assertThat(view).isPresent();
              assertThat(view.get().archivedPosition()).isGreaterThanOrEqualTo(processedPosition);
              assertThat(view.get().snapshots()).isNotEmpty();
              assertThat(view.get().chunks()).isNotEmpty();
            });

    broker.stop();
    final var archive = bucket.readAndVerify(TIMELINE, PARTITION);
    LOG.info("Verified the log archive: {}", archive.summary());
    assertThat(archive.startIndex()).isEqualTo(1);
    assertThat(archive.parentTimelineId()).isEmpty();
    assertThat(archive.archivedPosition()).isGreaterThanOrEqualTo(processedPosition);
    assertThat(archive.snapshots())
        .isNotEmpty()
        .allSatisfy(s -> assertThat(s.index()).isLessThanOrEqualTo(archive.archivedIndex()));
    assertThat(archive.chunks()).isNotEmpty();
    archive.chunks().forEach(chunk -> archive.view().readChunk(chunk));
    assertThat(archive.rootKeys()).isNotEmpty();
  }

  @Test
  void shouldNotCompactLogAboveArchivedPositionWhileTheStoreIsStuck() throws IOException {
    // given -- archiving works
    final long process;
    try (final var client = broker.newClientBuilder().build()) {
      process = deployProcess(client);
      createInstances(client, process, 20);
    }
    final long initialPosition = processedPosition();
    await("F reaches the processed position")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(bucket.tryView(TIMELINE, PARTITION))
                    .hasValueSatisfying(
                        view ->
                            assertThat(view.archivedPosition())
                                .isGreaterThanOrEqualTo(initialPosition)));

    // when -- the store rejects every new log object, and the broker processes more and takes a
    // snapshot
    final var dataDirectory = bucket.partitionDirectory(TIMELINE, PARTITION).resolve("data");
    Files.setPosixFilePermissions(dataDirectory, PosixFilePermissions.fromString("r-xr-xr-x"));
    try (final var client = broker.newClientBuilder().build()) {
      createInstances(client, process, 200);
    }
    partitions.takeSnapshot();
    await("a snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> assertThat(partitions.query().get(PARTITION).snapshotId()).isNotNull());

    // then -- the snapshot holds newer state than F, but its index, up to which the journal is
    // compacted, is not above F (I8)
    final var status = partitions.query().get(PARTITION);
    final long snapshotIndex =
        FileBasedSnapshotId.ofFileName(status.snapshotId()).getOrThrow().getIndex();
    final var stuckView = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
    assertThat(status.processedPositionInSnapshot())
        .as("the store is stuck, so the snapshot's state is ahead of F")
        .isGreaterThan(stuckView.archivedPosition());
    assertThat(snapshotIndex)
        .as("the snapshot index, which bounds compaction, is not above F")
        .isLessThanOrEqualTo(stuckView.archivedIndex());

    // when -- the store recovers
    Files.setPosixFilePermissions(dataDirectory, PosixFilePermissions.fromString("rwxr-xr-x"));
    final long stuckPosition = processedPosition();

    // then -- the archiver retries from F and catches up
    await("F catches up after the store recovers")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(bucket.tryView(TIMELINE, PARTITION).orElseThrow().archivedPosition())
                    .isGreaterThanOrEqualTo(stuckPosition));
    broker.stop();
    LOG.info("Verified the log archive: {}", bucket.readAndVerify(TIMELINE, PARTITION).summary());
  }

  private long processedPosition() {
    return partitions.query().get(PARTITION).processedPosition();
  }

  private long deployProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
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
      final CamundaClient client, final long process, final int count) {
    for (int i = 0; i < count; i++) {
      client
          .newCreateInstanceCommand()
          .processDefinitionKey(process)
          .variables(Map.of("payload", RandomStringUtils.insecure().nextAlphanumeric(1024)))
          .send()
          .join();
    }
  }
}
