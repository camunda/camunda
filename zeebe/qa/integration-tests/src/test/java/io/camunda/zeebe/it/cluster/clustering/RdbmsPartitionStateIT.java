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

import io.camunda.client.CamundaClient;
import io.camunda.configuration.data.PrimaryStorage.PrimaryStorageType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
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
 * A broker that keeps its partition's state in a relational database. Its snapshots hold no state,
 * so after a restart the broker must find in the database everything that the compacted log no
 * longer has.
 */
final class RdbmsPartitionStateIT {

  /** More log entries than raft keeps behind a snapshot, filling about three segments of 1 MB. */
  private static final int FILLER_MESSAGES = 200;

  private static final String FILLER_PAYLOAD = "x".repeat(16 * 1024);

  // the database lives as long as the JVM, so that it outlives the broker's connection pool
  private final String databaseUrl =
      "jdbc:h2:mem:zeebe-state-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
  private final String jobType = "task-" + UUID.randomUUID();

  @Test
  void shouldRecoverTheStateFromTheDatabaseAfterARestart(@TempDir final Path workingDirectory) {
    try (final var broker =
        new TestStandaloneBroker()
            .withUnauthenticatedAccess()
            .withWorkingDirectory(workingDirectory)
            .withUnifiedConfig(
                config -> {
                  final var primaryStorage = config.getData().getPrimaryStorage();
                  primaryStorage.setType(PrimaryStorageType.rdbms);
                  primaryStorage.getRdbms().setUrl(databaseUrl);
                  primaryStorage.getWriteBehindCache().setEnabled(true);
                  // small segments, so that the snapshot lets raft delete the oldest ones
                  primaryStorage.getLogStream().setLogSegmentSize(DataSize.ofMegabytes(1));
                  config.getCluster().getNetwork().setMaxMessageSize(DataSize.ofKilobytes(500));
                })
            .start()
            .awaitCompleteTopology()) {
      // given - instances that only the database has once the log is compacted, and instances
      // that the log has too
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
      final long termBeforeRestart = ownerTermOfPartitionOne();

      // when
      broker.stop();
      broker.start().awaitCompleteTopology();

      // then
      assertThat(stateRows()).isPositive();
      assertThat(ownerTermOfPartitionOne())
          .describedAs("the new leader claims the state with its own term")
          .isGreaterThan(termBeforeRestart);
      try (final var client = broker.newClientBuilder().build()) {
        assertThatAllJobsCanBeCompleted(processInstanceKeys, client, jobType);
      }
    }
  }

  private long ownerTermOfPartitionOne() {
    return queryLong("SELECT OWNER_TERM FROM ZEEBE_STATE_PARTITION WHERE PARTITION_ID = 1");
  }

  private long stateRows() {
    return queryLong("SELECT COUNT(*) FROM ZEEBE_STATE");
  }

  private long queryLong(final String sql) {
    try (final var connection = DriverManager.getConnection(databaseUrl);
        final var statement = connection.createStatement();
        final var result = statement.executeQuery(sql)) {
      assertThat(result.next()).describedAs("a row for: %s", sql).isTrue();
      return result.getLong(1);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to query the state database: " + sql, e);
    }
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
  static void awaitSnapshotOfEverythingProcessed(final PartitionsActuator partitions) {
    final long processedPosition = awaitProcessingIsIdle(partitions);
    partitions.takeSnapshot();
    Awaitility.await("The snapshot covers everything processed")
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> partitions.query().get(1),
            status ->
                status.snapshotId() != null
                    && Objects.requireNonNullElse(status.processedPositionInSnapshot(), -1L)
                        >= processedPosition);
  }

  /** Returns the processed position once it stopped changing. */
  static long awaitProcessingIsIdle(final PartitionsActuator partitions) {
    return Awaitility.await("Processing is idle")
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(2))
        .until(() -> partitions.query().get(1).processedPosition(), hasStableValue());
  }
}
