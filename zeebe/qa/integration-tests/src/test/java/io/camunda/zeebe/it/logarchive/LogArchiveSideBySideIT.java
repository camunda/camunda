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
import io.camunda.management.backups.PartitionBackupRange;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/**
 * Continuous backup and the log archive share one broker, one store and one compaction cap. Neither
 * breaks the other: backups complete and form one contiguous range, the log archive verifies and
 * covers the backups' checkpoints, and local compaction respects both pins.
 */
@ZeebeIntegration
final class LogArchiveSideBySideIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveSideBySideIT.class);
  private static final int PARTITION = 1;
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final Path dataDirectory = testDirectory.resolve("data").toAbsolutePath();
  private final String jobType = "job-" + UUID.randomUUID();

  @TestZeebe
  private final TestStandaloneBroker broker =
      LogArchiveBucket.enableLogArchive(
          new TestStandaloneBroker()
              .withUnifiedConfig(
                  cfg -> {
                    LogArchiveBucket.configureStore(cfg, basePath);
                    final var primaryStorage = cfg.getData().getPrimaryStorage();
                    primaryStorage.setDirectory(dataDirectory.toString());
                    primaryStorage.getLogStream().setLogSegmentSize(DataSize.ofMegabytes(5));
                    primaryStorage.getBackup().setContinuous(true);
                  }));

  private LogArchiveBucket bucket;
  private BackupActuator backups;
  private PartitionsActuator partitions;

  @BeforeEach
  void setUp() {
    bucket = new LogArchiveBucket(basePath);
    backups = BackupActuator.of(broker);
    partitions = PartitionsActuator.of(broker);
  }

  @AfterEach
  void tearDown() {
    bucket.close();
  }

  @Test
  void shouldRunContinuousBackupAndLogArchiveSideBySide() throws Exception {
    // given -- load, and two backups taken on demand
    final long firstBackup;
    final long secondBackup;
    try (final var client = broker.newClientBuilder().build()) {
      final long process = deployProcess(client);
      createInstances(client, process, 1000);
      firstBackup = takeAndAwaitBackup();
      createInstances(client, process, 1000);
      secondBackup = takeAndAwaitBackup();

      // when -- more load past the last backup, then a snapshot that triggers compaction
      createInstances(client, process, 1000);
    }
    final long processedPosition = processedPosition();
    partitions.takeSnapshot();
    await("a snapshot is taken and F reaches the processed position")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(snapshotIndex()).isGreaterThan(0);
              assertThat(bucket.tryView(TIMELINE, PARTITION))
                  .hasValueSatisfying(
                      view ->
                          assertThat(view.archivedPosition())
                              .isGreaterThanOrEqualTo(processedPosition));
            });

    // then -- backups still complete and form one contiguous range with its metadata
    final var state = backups.state();
    final var range = singleRangeOfPartition(state.getRanges());
    assertThat(range.getStart().getCheckpointId()).isLessThanOrEqualTo(firstBackup);
    assertThat(range.getEnd().getCheckpointId()).isGreaterThanOrEqualTo(secondBackup);
    assertThat(range.getStart().getCheckpointId()).isLessThan(range.getEnd().getCheckpointId());
    assertThat(backups.list())
        .extracting(info -> info.getBackupId())
        .contains(firstBackup, secondBackup);
    assertThat(basePath.resolve("metadata/" + PARTITION + "/metadata.json")).isRegularFile();
    final long lastBackupPosition = range.getEnd().getCheckpointPosition();
    final long lastBackupId = range.getEnd().getCheckpointId();

    broker.stop();

    // and -- the archive verifies, F follows the log, and both backups are restore points in it
    final var archive = bucket.readAndVerify(TIMELINE, PARTITION);
    LOG.info("Verified the log archive: {}", archive.summary());
    assertThat(archive.archivedPosition()).isGreaterThanOrEqualTo(processedPosition);
    final var covered =
        archive.commits().stream().flatMap(entry -> entry.checkpoints().stream()).toList();
    assertThat(covered)
        .extracting(CoveredCheckpointJson::checkpointId)
        .contains(firstBackup, secondBackup, lastBackupId);
    assertThat(covered)
        .filteredOn(c -> c.checkpointId() == lastBackupId)
        .extracting(CoveredCheckpointJson::checkpointPosition)
        .containsOnly(lastBackupPosition);

    // and -- compaction was capped by both pins: the journal still holds the entries at the last
    // backup's checkpoint position and at F. The cap itself is not observable from outside, so
    // this checks its effect; both pins lie below the snapshot's processed position, so without
    // the cap the snapshot would have allowed compacting them.
    final long cap = Math.min(lastBackupPosition, archive.archivedPosition());
    assertThat(cap).isLessThan(processedPosition);
    assertJournalHoldsPosition(lastBackupPosition);
    assertJournalHoldsPosition(archive.archivedPosition());
    assertJournalHoldsPosition(cap);
  }

  /**
   * The position is held by the retained journal: some retained entry starts at or below it, and
   * the journal was compacted at all.
   */
  private void assertJournalHoldsPosition(final long position) {
    final var partitionDirectory =
        dataDirectory.resolve("default").resolve("partitions").resolve(String.valueOf(PARTITION));
    try (final var journal =
            SegmentedJournal.builder(new SimpleMeterRegistry())
                .withDirectory(partitionDirectory.toFile())
                .withName("default-partition-" + PARTITION)
                .withMetaStore(new InMemory())
                .build();
        final var reader = journal.openReader()) {
      long firstApplicationAsqn = -1;
      long containingIndex = -1;
      while (reader.hasNext()) {
        final var entry = reader.next();
        if (entry.asqn() < 0) {
          continue;
        }
        if (firstApplicationAsqn < 0) {
          firstApplicationAsqn = entry.asqn();
        }
        if (entry.asqn() <= position) {
          containingIndex = entry.index();
        }
      }
      LOG.info(
          "Journal of partition {} starts at index {} (first position {}); position {} is in entry {}",
          PARTITION,
          journal.getFirstIndex(),
          firstApplicationAsqn,
          position,
          containingIndex);
      assertThat(journal.getFirstIndex())
          .as("the snapshot compacted the journal, so the pins are what keeps the entries")
          .isGreaterThan(1);
      assertThat(containingIndex)
          .as("journal still holds the entry containing position %d", position)
          .isGreaterThanOrEqualTo(journal.getFirstIndex());
    }
  }

  private static PartitionBackupRange singleRangeOfPartition(
      final List<PartitionBackupRange> ranges) {
    assertThat(ranges)
        .as("exactly one contiguous backup range for the partition")
        .extracting(PartitionBackupRange::getPartitionId)
        .containsExactly(PARTITION);
    return ranges.getFirst();
  }

  private long takeAndAwaitBackup() {
    final var response = backups.take();
    final var matcher = Pattern.compile("backup with id (\\d+)").matcher(response.getMessage());
    assertThat(matcher.find()).as(response.getMessage()).isTrue();
    final long backupId = Long.parseLong(matcher.group(1));
    await("backup " + backupId + " is completed")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(backups.status(backupId).getState()).isEqualTo(StateCode.COMPLETED));
    return backupId;
  }

  private long processedPosition() {
    return partitions.query().get(PARTITION).processedPosition();
  }

  private long snapshotIndex() {
    return FileBasedSnapshotId.ofFileName(partitions.query().get(PARTITION).snapshotId())
        .getOrThrow()
        .getIndex();
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
          .variables(Map.of("payload", RandomStringUtils.insecure().nextAlphanumeric(4096)))
          .send()
          .join();
    }
  }
}
