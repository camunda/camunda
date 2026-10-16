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
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestore;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreResult;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreTarget;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A whole cluster with several partitions is restored from the log archive: every broker restores
 * the partitions it hosts on its own, all agree on the checkpoint and the new timeline, and the
 * restored cluster holds exactly the state at the checkpoint on every partition.
 */
@ZeebeIntegration
final class LogArchiveClusterRestoreIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveClusterRestoreIT.class);
  private static final int BROKERS = 3;
  private static final int PARTITIONS = 2;
  private static final int BEFORE_SNAPSHOT = 12;
  private static final int BEFORE_CHECKPOINT = 8;
  private static final int AFTER_CHECKPOINT = 10;
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final String jobType = "job-" + UUID.randomUUID();

  @TestZeebe(autoStart = false)
  private final TestCluster source = cluster("source");

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
  void shouldRestoreAMultiPartitionClusterToACheckpoint() {
    // given -- instances before a snapshot, more before a checkpoint, and more after it
    source.start().awaitCompleteTopology();
    final long checkpointId;
    try (final var client = source.newClientBuilder().build()) {
      final long process = deployProcess(client);
      createInstances(client, process, "before", BEFORE_SNAPSHOT);
      awaitArchivedUpToProcessed();
      takeSnapshots();
      await("a snapshot is archived on every partition")
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  forEachPartition(
                      partition ->
                          assertThat(bucket.tryView(TIMELINE, partition).orElseThrow().snapshots())
                              .isNotEmpty()));

      createInstances(client, process, "before", BEFORE_CHECKPOINT);
      checkpointId = takeBackup();
      createInstances(client, process, "after", AFTER_CHECKPOINT);
    }
    await("F covers the checkpoint and an archived snapshot is older than it, on every partition")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                forEachPartition(
                    partition -> {
                      final var view = bucket.tryView(TIMELINE, partition).orElseThrow();
                      final var checkpoint = view.findCheckpoint(checkpointId);
                      assertThat(checkpoint).isPresent();
                      assertThat(view.snapshots())
                          .anySatisfy(
                              s ->
                                  assertThat(s.processedPosition())
                                      .isLessThan(checkpoint.get().checkpointPosition()));
                    }));
    source.shutdown();
    forEachPartition(
        partition ->
            LOG.info(
                "Verified the source log archive of partition {}: {}",
                partition,
                bucket.readAndVerify(TIMELINE, partition).summary()));

    // when -- every broker restores its partitions into an empty data directory
    final var restore =
        new LogArchiveRestore(
            bucket.store(), new RocksDBSnapshotFileInfoProvider(), new SimpleMeterRegistry());
    final var results = new ArrayList<LogArchiveRestoreResult>();
    for (final var brokerId : brokerIds()) {
      final var targets =
          IntStream.rangeClosed(1, PARTITIONS)
              .mapToObj(
                  partition ->
                      new LogArchiveRestoreTarget(
                          partition,
                          dataDirectory("restored", brokerId)
                              .resolve("default/partitions/" + partition),
                          "default-partition-" + partition,
                          Integer.parseInt(brokerId.id())))
              .toList();
      final var result =
          restore.restorePartitions(TIMELINE, PARTITIONS, Optional.of(checkpointId), targets);
      LOG.info("Broker {} restored {}", brokerId.id(), result.partitions());
      results.add(result);
    }
    final String newTimeline = TIMELINE + ".r" + checkpointId;
    assertThat(results)
        .hasSize(BROKERS)
        .allSatisfy(
            result -> {
              assertThat(result.timelineId()).isEqualTo(newTimeline);
              assertThat(result.restorePoint()).isEqualTo(results.getFirst().restorePoint());
              assertThat(result.restorePoint().checkpointId()).isEqualTo(checkpointId);
              assertThat(result.partitions()).containsOnlyKeys(1, 2);
              assertThat(result.partitions().values())
                  .allSatisfy(
                      p -> {
                        assertThat(p.snapshotId()).isPresent();
                        assertThat(p.timelineId()).isEqualTo(newTimeline);
                      });
            });

    // and -- the brokers start on the restored data; the cluster is built only now, because the
    // ports of clusters built together can collide
    final var restored = cluster("restored");
    try (restored) {
      restored.start().awaitCompleteTopology();
      assertRestored(restored, newTimeline);
    }
    forEachPartition(
        partition -> {
          final var archive = bucket.readAndVerify(newTimeline, partition);
          LOG.info(
              "Verified the restored log archive of partition {}: {}",
              partition,
              archive.summary());
          assertThat(archive.parentTimelineId()).isEqualTo(TIMELINE);
        });
  }

  private void assertRestored(final TestCluster restored, final String newTimeline) {

    // then -- exactly the jobs created before the checkpoint exist, on both partitions
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
      assertThat(activated)
          .extracting(job -> Protocol.decodePartitionId(job.getKey()))
          .containsOnly(1, 2)
          .contains(1, 2);
      assertThat(activated).extracting(ActivatedJob::getKey).doesNotHaveDuplicates();
      assertThat(activateJobs(client)).as("no job created after the checkpoint").isEmpty();
    }

    // and -- both partitions archive into the new timeline
    await("both partitions archive into the new timeline")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                forEachPartition(
                    partition ->
                        assertThat(bucket.tryView(newTimeline, partition))
                            .hasValueSatisfying(
                                view ->
                                    assertThat(view.archivedIndex())
                                        .isGreaterThanOrEqualTo(view.startIndex()))));
    restored.shutdown();
    forEachPartition(
        partition -> {
          final var archive = bucket.readAndVerify(newTimeline, partition);
          LOG.info(
              "Verified the restored log archive of partition {}: {}",
              partition,
              archive.summary());
          assertThat(archive.parentTimelineId()).isEqualTo(TIMELINE);
        });
  }

  private TestCluster cluster(final String name) {
    return TestCluster.builder()
        .withBrokersCount(BROKERS)
        .withPartitionsCount(PARTITIONS)
        .withReplicationFactor(BROKERS)
        .withEmbeddedGateway(true)
        .withBrokerConfig(
            (id, broker) ->
                LogArchiveBucket.enableLogArchive(
                    broker.withUnifiedConfig(
                        cfg -> {
                          LogArchiveBucket.configureStore(cfg, basePath);
                          cfg.getData()
                              .getPrimaryStorage()
                              .setDirectory(dataDirectory(name, id).toString());
                        })))
        .build();
  }

  private Path dataDirectory(final String cluster, final MemberId brokerId) {
    return testDirectory.resolve(cluster + "-broker-" + brokerId.id()).toAbsolutePath();
  }

  private List<MemberId> brokerIds() {
    return source.brokers().keySet().stream().sorted().toList();
  }

  private static void forEachPartition(final java.util.function.IntConsumer action) {
    IntStream.rangeClosed(1, PARTITIONS).forEach(action);
  }

  private void awaitArchivedUpToProcessed() {
    final var processed = new java.util.HashMap<Integer, Long>();
    source
        .brokers()
        .values()
        .forEach(
            broker ->
                PartitionsActuator.of(broker)
                    .query()
                    .forEach(
                        (partition, status) ->
                            processed.merge(partition, status.processedPosition(), Math::max)));
    await("F reaches the processed position of every partition " + processed)
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                processed.forEach(
                    (partition, position) ->
                        assertThat(bucket.tryView(TIMELINE, partition))
                            .hasValueSatisfying(
                                view ->
                                    assertThat(view.archivedPosition())
                                        .isGreaterThanOrEqualTo(position))));
  }

  private void takeSnapshots() {
    source.brokers().values().forEach(broker -> PartitionsActuator.of(broker).takeSnapshot());
  }

  private long takeBackup() {
    final var backupActuator = BackupActuator.of(source.brokers().values().iterator().next());
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
