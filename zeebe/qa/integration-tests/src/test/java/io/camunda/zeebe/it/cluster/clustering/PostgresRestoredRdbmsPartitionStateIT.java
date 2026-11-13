/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.zeebe.it.cluster.clustering.RdbmsPartitionStateIT.awaitProcessingIsIdle;
import static io.camunda.zeebe.it.cluster.clustering.RdbmsPartitionStateIT.awaitSnapshotOfEverythingProcessed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.data.PrimaryStorage.PrimaryStorageType;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.util.health.HealthReport;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A broker whose state database was restored to an earlier point in time, for example from a
 * database backup. Its latest snapshot names a processed position that the restored state no longer
 * has, and raft may already have deleted the log that the state would need to catch up.
 */
@Testcontainers
final class PostgresRestoredRdbmsPartitionStateIT {

  @Container
  private static final PostgreSQLContainer SERVER = new PostgreSQLContainer("postgres:15-alpine");

  private final String databaseName = uniqueDatabaseName();
  private final String earlierCopyName = uniqueDatabaseName();
  private final String laterStateName = uniqueDatabaseName();

  @AfterEach
  void dropDatabases() {
    Stream.of(databaseName, earlierCopyName, laterStateName)
        .forEach(name -> execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)"));
  }

  @Test
  void shouldStopThePartitionWhenTheStateIsOlderThanTheSnapshot(
      @TempDir final Path workingDirectory) {
    // given - a copy of the state taken before the latest snapshot
    execute("CREATE DATABASE " + databaseName);
    try (final var broker = createBroker(workingDirectory)) {
      broker.start().awaitCompleteTopology();
      try (final var client = broker.newClientBuilder().build()) {
        deployProcess(client);
        createProcessInstances(client, 5);
      }
      awaitProcessingIsIdle(PartitionsActuator.of(broker));
      broker.stop();
      copyDatabase(databaseName, earlierCopyName);

      broker.start().awaitCompleteTopology();
      try (final var client = broker.newClientBuilder().build()) {
        createProcessInstances(client, 5);
      }
      awaitSnapshotOfEverythingProcessed(PartitionsActuator.of(broker));
      broker.stop();

      // when - the state is restored in place to the earlier copy
      renameDatabase(databaseName, laterStateName);
      renameDatabase(earlierCopyName, databaseName);
      broker.start();

      // then
      Awaitility.await("The partition stops because its state is older than its snapshot")
          .atMost(Duration.ofMinutes(1))
          .untilAsserted(
              () -> {
                final var health = healthOfPartitionOne(broker);
                assertThat(health.isDead()).describedAs("partition is dead: %s", health).isTrue();
                assertThat(failureMessages(health))
                    .anyMatch(message -> message.contains("processed only up to position"));
              });
    }
  }

  private TestStandaloneBroker createBroker(final Path workingDirectory) {
    return new TestStandaloneBroker()
        .withUnauthenticatedAccess()
        .withWorkingDirectory(workingDirectory)
        .withSecondaryStorageType(SecondaryStorageType.rdbms)
        .withUnifiedConfig(
            config -> {
              final var rdbms = config.getData().getSecondaryStorage().getRdbms();
              rdbms.setUrl(databaseUrl());
              rdbms.setUsername(SERVER.getUsername());
              rdbms.setPassword(SERVER.getPassword());
              final var primaryStorage = config.getData().getPrimaryStorage();
              primaryStorage.setType(PrimaryStorageType.rdbms);
              primaryStorage.getWriteBehindCache().setEnabled(true);
            });
  }

  private String databaseUrl() {
    return "jdbc:postgresql://%s:%d/%s"
        .formatted(
            SERVER.getHost(),
            SERVER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            databaseName);
  }

  private static HealthReport healthOfPartitionOne(final TestStandaloneBroker broker) {
    return broker
        .bean(Broker.class)
        .getBrokerContext()
        .getPartitionManagers()
        .get(DEFAULT_PHYSICAL_TENANT_ID)
        .getZeebePartitions()
        .stream()
        .filter(partition -> partition.getPartitionId() == 1)
        .findFirst()
        .orElseThrow()
        .getHealthReport();
  }

  /** The messages of every failure in the report, its children, and their causes. */
  private static List<String> failureMessages(final HealthReport report) {
    final List<String> messages = new ArrayList<>();
    final var issue = report.getIssue();
    if (issue != null) {
      for (Throwable error = issue.throwable(); error != null; error = error.getCause()) {
        messages.add(String.valueOf(error.getMessage()));
      }
      if (issue.cause() != null) {
        messages.addAll(failureMessages(issue.cause()));
      }
    }
    report.children().values().forEach(child -> messages.addAll(failureMessages(child)));
    return messages;
  }

  private static void deployProcess(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process").startEvent().endEvent().done(), "process.bpmn")
        .send()
        .join();
  }

  private static void createProcessInstances(final CamundaClient client, final int count) {
    for (int i = 0; i < count; i++) {
      client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
    }
  }

  private static void copyDatabase(final String source, final String copy) {
    disconnectFrom(source);
    execute("CREATE DATABASE " + copy + " TEMPLATE " + source);
  }

  private static void renameDatabase(final String name, final String newName) {
    disconnectFrom(name);
    execute("ALTER DATABASE " + name + " RENAME TO " + newName);
  }

  /** Postgres copies and renames a database only while nobody is connected to it. */
  private static void disconnectFrom(final String database) {
    execute(
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%s'"
            .formatted(database));
  }

  private static void execute(final String sql) {
    try (final var connection =
            DriverManager.getConnection(
                SERVER.getJdbcUrl(), SERVER.getUsername(), SERVER.getPassword());
        final var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to run: " + sql, e);
    }
  }

  private static String uniqueDatabaseName() {
    return "zeebe_state_" + UUID.randomUUID().toString().replace("-", "");
  }
}
