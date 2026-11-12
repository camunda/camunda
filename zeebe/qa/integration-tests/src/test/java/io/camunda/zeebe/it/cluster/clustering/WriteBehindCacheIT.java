/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static io.camunda.zeebe.it.cluster.clustering.dynamic.Utils.assertThatAllJobsCanBeCompleted;
import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Data;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator.PartitionStatus;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

/**
 * Brokers that keep their partitions' state behind the write-behind cache. Its writer would hold
 * every write back for an hour, and never fills a batch, so RocksDB only gets the state when a
 * snapshot makes the writer catch up.
 */
final class WriteBehindCacheIT {

  private static final Duration CHANGE_TIMEOUT = Duration.ofMinutes(2);

  /** More log entries than raft keeps behind a snapshot, filling about three segments of 1 MB. */
  private static final int FILLER_MESSAGES = 200;

  private static final String FILLER_PAYLOAD = "x".repeat(16 * 1024);

  private final String jobType = "task-" + UUID.randomUUID();

  @Test
  void shouldRecoverTheStateFromASnapshotAfterARestart(@TempDir final Path workingDirectory) {
    try (final var broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withWorkingDirectory(workingDirectory)
            .withUnifiedConfig(
                config -> {
                  enableWriteBehindCache(config.getData());
                  // small segments, so that the snapshot lets raft delete the oldest ones
                  config
                      .getData()
                      .getPrimaryStorage()
                      .getLogStream()
                      .setLogSegmentSize(DataSize.ofMegabytes(1));
                  config.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(500));
                })
            .start()
            .awaitCompleteTopology()) {
      // given - instances that only the snapshot has once the log is compacted, and instances
      // that only the log has
      final var partitions = PartitionsActuator.of(broker);
      final var logDirectory = workingDirectory.resolve("data/default/partitions/1");
      final List<Long> processInstanceKeys = new ArrayList<>();
      try (final var client = broker.newClientBuilder().build()) {
        deployProcess(client);
        processInstanceKeys.addAll(createProcessInstances(client, 10));
        publishFillerMessages(client);
        final var segmentsBeforeSnapshot = segments(logDirectory);
        awaitSnapshotOfEverythingProcessed(partitions);
        Awaitility.await("Raft deletes the segments the snapshot replaces")
            .atMost(Duration.ofSeconds(30))
            .until(() -> !segments(logDirectory).containsAll(segmentsBeforeSnapshot));
        processInstanceKeys.addAll(createProcessInstances(client, 5));
      }

      // when
      broker.stop();
      broker.start().awaitCompleteTopology();

      // then
      try (final var client = broker.newClientBuilder().build()) {
        assertThatAllJobsCanBeCompleted(processInstanceKeys, client, jobType);
      }
    }
  }

  @Test
  void shouldServeTheStateOfAReplicatedSnapshot() {
    // two partitions, so that each broker has one to start with
    try (final var cluster =
        TestCluster.builder()
            .withBrokersCount(2)
            .withPartitionsCount(2)
            .withReplicationFactor(1)
            .withGatewaysCount(1)
            .withGatewayConfig(gateway -> gateway.withCreateSchema(false))
            .withBrokerConfig(
                broker ->
                    broker
                        .withCreateSchema(false)
                        .withDataConfig(WriteBehindCacheIT::enableWriteBehindCache))
            .build()
            .start()
            .awaitCompleteTopology()) {
      // given - a snapshot of the only replica of partition 1, on broker 0
      final var broker0 = cluster.brokers().get(MemberId.from("0"));
      assertThat(cluster.leaderForPartition(1)).isSameAs(broker0);
      final var leader = PartitionsActuator.of(broker0);
      final List<Long> processInstanceKeys;
      try (final var client = cluster.newClientBuilder().build()) {
        deployProcess(client);
        processInstanceKeys = createProcessInstances(client, 10);
      }
      final var snapshotId = awaitSnapshotOfEverythingProcessed(leader).snapshotId();

      // when - broker 1 joins partition 1, so it gets that snapshot, and then takes it over
      final var newReplica = PartitionsActuator.of(cluster.brokers().get(MemberId.from("1")));
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      awaitChange(cluster, actuator.joinPartition(1, 1, 1));
      Awaitility.await("The new replica has the leader's snapshot")
          .atMost(CHANGE_TIMEOUT)
          .untilAsserted(
              () -> assertThat(newReplica.query().get(1).snapshotId()).isEqualTo(snapshotId));
      awaitChange(cluster, actuator.leavePartition(0, 1));
      cluster.awaitHealthyTopology();

      // then
      try (final var client = cluster.newClientBuilder().build()) {
        assertThatAllJobsCanBeCompleted(processInstanceKeys, client, jobType);
      }
    }
  }

  private static void enableWriteBehindCache(final Data data) {
    final var writeBehindCache = data.getPrimaryStorage().getWriteBehindCache();
    writeBehindCache.setEnabled(true);
    writeBehindCache.setMaxWriteDelay(Duration.ofHours(1));
    writeBehindCache.setBatchSize(1_000_000);
  }

  private static void publishFillerMessages(final CamundaClient client) {
    for (int i = 0; i < FILLER_MESSAGES; i++) {
      client
          .newPublishMessageCommand()
          .messageName("filler")
          .correlationKey("filler-" + i)
          .timeToLive(Duration.ZERO)
          .variables(Map.of("payload", FILLER_PAYLOAD))
          .send()
          .join();
    }
  }

  private static Set<Path> segments(final Path logDirectory) {
    try (final Stream<Path> files = Files.list(logDirectory)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".log"))
          .collect(Collectors.toSet());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void deployProcess(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", task -> task.zeebeJobType(jobType))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();
  }

  private static List<Long> createProcessInstances(final CamundaClient client, final int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId("process")
                    .latestVersion()
                    .send()
                    .join()
                    .getProcessInstanceKey())
        .toList();
  }

  /** Takes a snapshot once processing is idle, and waits until it covers everything processed. */
  private static PartitionStatus awaitSnapshotOfEverythingProcessed(
      final PartitionsActuator partitions) {
    final long processedPosition =
        Awaitility.await("Processing is idle")
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(2))
            .until(() -> partitions.query().get(1).processedPosition(), hasStableValue());
    partitions.takeSnapshot();
    return Awaitility.await("The snapshot covers everything processed")
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> partitions.query().get(1),
            status ->
                status.snapshotId() != null
                    && Objects.requireNonNullElse(status.processedPositionInSnapshot(), -1L)
                        >= processedPosition);
  }

  private static void awaitChange(
      final TestCluster cluster, final PlannedOperationsResponse response) {
    assertThat(response.getPlannedChanges()).isNotEmpty();
    Awaitility.await("The cluster change completes")
        .atMost(CHANGE_TIMEOUT)
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(cluster).hasCompletedChanges(response));
  }
}
