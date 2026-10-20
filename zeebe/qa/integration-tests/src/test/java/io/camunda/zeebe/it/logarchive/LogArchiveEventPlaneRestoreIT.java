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
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.TimelineFile;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.it.logarchive.StubEventPlane.TimelineKey;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreTarget;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A cluster restored from the log archive announces its new timeline to the event plane with the
 * point where it takes over its parent: the checkpoint's position and the index of its entry (spec
 * section 19.3). The same values are in {@code timeline.json}.
 */
@ZeebeIntegration
final class LogArchiveEventPlaneRestoreIT {

  private static final int PARTITION = 1;
  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final Path restoredData = testDirectory.resolve("restored-data").toAbsolutePath();
  private final String clusterId = "cluster-" + UUID.randomUUID();
  private final StubEventPlane eventPlane = new StubEventPlane();

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
    try {
      source.stop();
      restored.stop();
    } finally {
      bucket.close();
      eventPlane.close();
    }
  }

  @Test
  void shouldAnnounceTheRestoredTimelineWithItsTakeoverPoint() {
    // given -- a source with a checkpoint and a snapshot older than it
    final long checkpointId;
    try (final var client = source.newClientBuilder().build()) {
      final long process = deployProcess(client);
      createInstances(client, process, 15);
      final long processed =
          PartitionsActuator.of(source).query().get(PARTITION).processedPosition();
      await("F reaches the processed position")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(bucket.tryView(TIMELINE, PARTITION))
                      .hasValueSatisfying(
                          v -> assertThat(v.archivedPosition()).isGreaterThanOrEqualTo(processed)));
      PartitionsActuator.of(source).takeSnapshot();
      await("a snapshot is archived")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(bucket.tryView(TIMELINE, PARTITION).orElseThrow().snapshots())
                      .isNotEmpty());
      createInstances(client, process, 10);
      checkpointId = takeBackup();
      createInstances(client, process, 10);
    }
    await("F covers the checkpoint and an archived snapshot is older than it")
        .atMost(TIMEOUT)
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
    final long checkpointPosition =
        bucket
            .tryView(TIMELINE, PARTITION)
            .orElseThrow()
            .findCheckpoint(checkpointId)
            .orElseThrow()
            .checkpointPosition();
    source.stop();

    // when -- the partition is restored to the checkpoint and the restored broker starts
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
    final String newTimeline = TIMELINE + ".r" + checkpointId;
    restored.start().awaitCompleteTopology();

    // then -- the event plane gets the new timeline from seq 1, with the takeover point
    final var key = new TimelineKey(clusterId, "default", PARTITION, newTimeline);
    await("the event plane has the started entry of the new timeline")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(eventPlane.entries(key)).isNotEmpty());
    final var started = eventPlane.entries(key).getFirst();
    assertThat(started.get("seq").asLong()).isEqualTo(1);
    assertThat(started.get("type").asText()).isEqualTo("TIMELINE_STARTED");
    assertThat(started.get("parentTimelineId").asText()).isEqualTo(TIMELINE);
    assertThat(started.get("parentPosition").asLong()).isEqualTo(checkpointPosition);
    assertThat(started.get("parentIndex").asLong()).isPositive();
    assertThat(started.get("startedAt").asLong()).isPositive();

    // and -- timeline.json in the bucket carries the same values
    final var timelineFile =
        LogArchiveJson.read(
            bucket
                .store()
                .getIfExists(
                    LogArchiveKeys.partitionPrefix(newTimeline, PARTITION)
                        + LogArchiveKeys.timelineKey())
                .join()
                .orElseThrow(),
            TimelineFile.class);
    assertThat(timelineFile.parentTimelineId()).isEqualTo(TIMELINE);
    assertThat(timelineFile.parentPosition()).isEqualTo(started.get("parentPosition").asLong());
    assertThat(timelineFile.parentIndex()).isEqualTo(started.get("parentIndex").asLong());
    assertThat(timelineFile.startedAt()).isEqualTo(started.get("startedAt").asLong());
    assertThat(timelineFile.startIndex()).isEqualTo(started.get("startIndex").asLong());
  }

  private TestStandaloneBroker broker(final Path dataDirectory) {
    final var prefix = "zeebe.broker.data.backup.logArchive.eventPlane.";
    return LogArchiveBucket.enableLogArchive(
            new TestStandaloneBroker()
                .withUnifiedConfig(
                    cfg -> {
                      LogArchiveBucket.configureStore(cfg, basePath);
                      cfg.getData()
                          .getPrimaryStorage()
                          .setDirectory(dataDirectory.toAbsolutePath().toString());
                    }))
        .withProperty(prefix + "url", eventPlane.url())
        .withProperty(prefix + "clusterId", clusterId)
        .withProperty(prefix + "requestTimeout", "PT2S");
  }

  private long takeBackup() {
    final var backupActuator = BackupActuator.of(source);
    final long backupId = System.currentTimeMillis();
    backupActuator.take(backupId);
    await("backup is completed")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));
    return backupId;
  }

  private static long deployProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("job-" + UUID.randomUUID()))
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
      client.newCreateInstanceCommand().processDefinitionKey(process).send().join();
    }
  }
}
