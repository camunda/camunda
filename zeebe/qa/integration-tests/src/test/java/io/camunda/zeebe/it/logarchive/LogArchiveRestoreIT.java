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
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreTarget;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A partition restored from the log archive to a checkpoint holds exactly the state of the source
 * at that checkpoint, and the restored broker archives into a new timeline that starts at the
 * restored journal.
 */
@ZeebeIntegration
final class LogArchiveRestoreIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveRestoreIT.class);
  private static final int PARTITION = 1;
  private static final int BEFORE_SNAPSHOT = 20;
  private static final int BEFORE_CHECKPOINT = 10;
  private static final int AFTER_CHECKPOINT = 15;
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final Path sourceData = testDirectory.resolve("source-data").toAbsolutePath();
  private final Path restoredData = testDirectory.resolve("restored-data").toAbsolutePath();
  private final String jobType = "job-" + UUID.randomUUID();

  @TestZeebe private final TestStandaloneBroker source = broker(sourceData);

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
  void shouldRestoreToCheckpointAndArchiveIntoANewTimeline() {
    // given -- instances before a snapshot, more before a checkpoint, and more after it
    final var partitions = PartitionsActuator.of(source);
    final long checkpointId;
    try (final var client = source.newClientBuilder().build()) {
      final long process = deployProcess(client);
      createInstances(client, process, "before", BEFORE_SNAPSHOT);
      awaitArchivedUpTo(processedPosition(partitions));
      partitions.takeSnapshot();
      await("a snapshot is archived")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  assertThat(bucket.tryView(TIMELINE, PARTITION).orElseThrow().snapshots())
                      .isNotEmpty());

      createInstances(client, process, "before", BEFORE_CHECKPOINT);
      checkpointId = takeBackup();
      createInstances(client, process, "after", AFTER_CHECKPOINT);
    }
    await("F covers the checkpoint and an archived snapshot is older than the checkpoint")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              final var checkpoint = view.findCheckpoint(checkpointId);
              assertThat(checkpoint).isPresent();
              assertThat(view.snapshots())
                  .anySatisfy(
                      s ->
                          assertThat(s.processedPosition())
                              .isLessThan(checkpoint.get().checkpointPosition()));
            });
    source.stop();
    LOG.info(
        "Verified the source log archive: {}", bucket.readAndVerify(TIMELINE, PARTITION).summary());

    // when -- the partition is restored into an empty data directory
    final var result =
        new LogArchiveRestore(
                bucket.store(), new RocksDBSnapshotFileInfoProvider(), new SimpleMeterRegistry())
            .restorePartitions(
                TIMELINE,
                1,
                Optional.of(checkpointId),
                List.of(
                    new LogArchiveRestoreTarget(
                        PARTITION,
                        restoredData.resolve("default/partitions/" + PARTITION),
                        "default-partition-" + PARTITION,
                        0)));
    final var restoredPartition = result.partitions().get(PARTITION);
    LOG.info("Restored {}", restoredPartition);
    final String newTimeline = TIMELINE + ".r" + checkpointId;
    assertThat(result.timelineId()).isEqualTo(newTimeline);
    assertThat(restoredPartition.snapshotId()).isPresent();
    assertThat(restoredPartition.timelineId()).isEqualTo(newTimeline);

    // and -- a broker starts on the restored data
    restored.start().awaitCompleteTopology();

    // then -- exactly the jobs created before the checkpoint exist
    try (final var client = restored.newClientBuilder().build()) {
      final var activated = new ArrayList<ActivatedJob>();
      await("all jobs created before the checkpoint are activated")
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(500))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                activated.addAll(activateJobs(client));
                assertThat(activated).hasSize(BEFORE_SNAPSHOT + BEFORE_CHECKPOINT);
              });
      assertThat(activated)
          .extracting(job -> job.getVariablesAsMap().get("phase"))
          .containsOnly("before");
      assertThat(activateJobs(client)).as("no job created after the checkpoint").isEmpty();
    }

    // and -- the restored broker archives into the new timeline, from the restored journal on
    await("the restored broker archives into the new timeline beyond the restored journal")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(bucket.tryView(newTimeline, PARTITION))
                    .hasValueSatisfying(
                        view ->
                            assertThat(view.archivedIndex())
                                .isGreaterThan(restoredPartition.lastIndex())));
    restored.stop();
    final var archive = bucket.readAndVerify(newTimeline, PARTITION);
    LOG.info("Verified the restored log archive: {}", archive.summary());
    assertThat(archive.parentTimelineId()).isEqualTo(TIMELINE);
    assertThat(archive.startIndex()).isEqualTo(restoredPartition.firstIndex());
    assertThat(
            Files.exists(
                restoredData.resolve(
                    "default/partitions/"
                        + PARTITION
                        + "/"
                        + LogArchiveRestore.TIMELINE_FILE_NAME)))
        .isTrue();
  }

  private TestStandaloneBroker broker(final Path dataDirectory) {
    return LogArchiveBucket.enableLogArchive(
        new TestStandaloneBroker()
            .withUnifiedConfig(
                cfg -> {
                  LogArchiveBucket.configureStore(cfg, basePath);
                  cfg.getData().getPrimaryStorage().setDirectory(dataDirectory.toString());
                }));
  }

  private long takeBackup() {
    final var backupActuator = BackupActuator.of(source);
    final long backupId = System.currentTimeMillis();
    backupActuator.take(backupId);
    await("backup is completed")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));
    return backupId;
  }

  private void awaitArchivedUpTo(final long position) {
    await("F reaches position " + position)
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(bucket.tryView(TIMELINE, PARTITION))
                    .hasValueSatisfying(
                        view ->
                            assertThat(view.archivedPosition()).isGreaterThanOrEqualTo(position)));
  }

  private static long processedPosition(final PartitionsActuator partitions) {
    return partitions.query().get(PARTITION).processedPosition();
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
}
