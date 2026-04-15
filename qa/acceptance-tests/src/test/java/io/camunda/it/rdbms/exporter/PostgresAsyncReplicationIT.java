/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.exporter.rdbms.RdbmsExporterWrapper;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Integration test that validates async replication awareness in the RDBMS exporter. Uses a
 * PostgreSQL primary + replica cluster to verify that:
 *
 * <ul>
 *   <li>When the replica is in sync, the broker position advances after export
 *   <li>When replication is paused, the broker position does NOT advance (log preserved)
 *   <li>When replication resumes, the broker position eventually catches up
 * </ul>
 */
@Tag("rdbms")
@SpringBootTest(classes = {RdbmsTestConfiguration.class})
@TestPropertySource(
    properties = {
      "spring.liquibase.enabled=false",
      "camunda.data.secondary-storage.type=rdbms",
      "camunda.data.secondary-storage.rdbms.queue-size=0",
    })
class PostgresAsyncReplicationIT {

  static final Network network = Network.newNetwork();

  @SuppressWarnings("resource")
  static final GenericContainer<?> primary =
      new GenericContainer<>("bitnamilegacy/postgresql:15")
          .withNetwork(network)
          .withNetworkAliases("primary")
          .withEnv("POSTGRESQL_REPLICATION_MODE", "master")
          .withEnv("POSTGRESQL_REPLICATION_USER", "repl_user")
          .withEnv("POSTGRESQL_REPLICATION_PASSWORD", "repl_pass")
          .withEnv("POSTGRESQL_PASSWORD", "secret")
          .withEnv("POSTGRESQL_DATABASE", "testdb")
          .withExposedPorts(5432)
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 1)
                  .withStartupTimeout(Duration.ofSeconds(60)));

  @SuppressWarnings("resource")
  static final GenericContainer<?> replica =
      new GenericContainer<>("bitnamilegacy/postgresql:15")
          .withNetwork(network)
          .withEnv("POSTGRESQL_REPLICATION_MODE", "slave")
          .withEnv("POSTGRESQL_MASTER_HOST", "primary")
          .withEnv("POSTGRESQL_REPLICATION_USER", "repl_user")
          .withEnv("POSTGRESQL_REPLICATION_PASSWORD", "repl_pass")
          .withEnv("POSTGRESQL_PASSWORD", "secret")
          .withExposedPorts(5432)
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept.*connections.*\\s", 1)
                  .withStartupTimeout(Duration.ofSeconds(60)));

  private static final RecordFixtures FIXTURES = new RecordFixtures();

  static {
    primary.start();
    replica.start();
    waitForReplication();
  }

  private final ExporterTestController controller = new ExporterTestController();
  private final VendorDatabaseProperties vendorDatabaseProperties =
      new VendorDatabaseProperties(
          new Properties() {
            {
              setProperty("variableValue.previewSize", "100");
              setProperty("userCharColumn.size", "50");
              setProperty("errorMessage.size", "500");
              setProperty("treePath.size", "500");
              setProperty("disableFkBeforeTruncate", "true");
            }
          });
  @Autowired private LiquibaseSchemaManager liquibaseSchemaManager;
  @Autowired private RdbmsService rdbmsService;
  @Autowired private ExporterPositionMapper exporterPositionMapper;
  private RdbmsExporterWrapper exporter;

  @AfterAll
  static void stopContainers() {
    replica.stop();
    primary.stop();
    network.close();
  }

  @DynamicPropertySource
  static void configureDataSource(final DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://"
                + primary.getHost()
                + ":"
                + primary.getMappedPort(5432)
                + "/testdb");
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "secret");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @BeforeEach
  void setUp() {
    exporter =
        new RdbmsExporterWrapper(rdbmsService, liquibaseSchemaManager, vendorDatabaseProperties);

    final Map<String, Object> config =
        Map.of(
            "queueSize",
            0,
            "asyncReplication",
            Map.of("enabled", true, "pollingInterval", "PT1S", "minSyncReplicas", 1));

    exporter.configure(
        new ExporterContext(
            null,
            new ExporterConfiguration("foo", config),
            1,
            Mockito.mock(
                io.micrometer.core.instrument.MeterRegistry.class, Mockito.RETURNS_DEEP_STUBS),
            null));
    exporter.open(controller);
  }

  @AfterEach
  void tearDown() {
    if (exporter != null) {
      exporter.close();
    }
  }

  @Test
  void shouldNotAdvanceBrokerPositionWhenReplicaIsPausedAndCatchUpAfterResume() throws Exception {
    // given — export records while replica is in sync
    waitForReplicasInSync();

    // Export initial records and wait for broker position to advance
    for (int i = 0; i < 5; i++) {
      exporter.export(FIXTURES.getProcessInstanceStartedRecord());
    }

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              exporter.export(FIXTURES.getProcessInstanceStartedRecord());
              controller.runScheduledTasks(Duration.ofSeconds(2));
              assertThat(controller.getPosition()).isGreaterThan(0);
            });

    // when — pause WAL replay on the replica
    pauseReplicaReplay();

    // Export records while replica is paused — these create WAL entries on the primary
    // that the replica cannot replay yet
    for (int i = 0; i < 10; i++) {
      exporter.export(FIXTURES.getProcessInstanceStartedRecord());
    }

    // Wait for position to stabilize: same value across two consecutive polls.
    // We update lastSeen BEFORE the assertion so it persists even when the assertion fails.
    final long[] lastSeen = {-1};
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(3))
        .untilAsserted(
            () -> {
              exporter.export(FIXTURES.getProcessInstanceStartedRecord());
              controller.runScheduledTasks(Duration.ofSeconds(2));
              final long previous = lastSeen[0];
              final long current = controller.getPosition();
              lastSeen[0] = current;
              assertThat(current)
                  .as("Position should stabilize (stop advancing)")
                  .isEqualTo(previous);
            });

    // Capture the stabilized position after the pause has taken effect
    final long positionAfterPauseStabilized = controller.getPosition();

    // Export more records to generate new WAL entries that the paused replica cannot confirm
    for (int i = 0; i < 10; i++) {
      exporter.export(FIXTURES.getProcessInstanceStartedRecord());
    }

    // then — verify broker position does NOT advance while replica is paused.
    // The `during` clause ensures the condition holds for the entire 5-second window.
    await()
        .during(Duration.ofSeconds(5))
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              exporter.export(FIXTURES.getProcessInstanceStartedRecord());
              controller.runScheduledTasks(Duration.ofSeconds(2));
              assertThat(controller.getPosition()).isEqualTo(positionAfterPauseStabilized);
            });

    // when — resume WAL replay on the replica
    resumeReplicaReplay();

    // then — eventually the broker position should advance as the replica catches up
    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              exporter.export(FIXTURES.getProcessInstanceStartedRecord());
              controller.runScheduledTasks(Duration.ofSeconds(2));
              assertThat(controller.getPosition()).isGreaterThan(positionAfterPauseStabilized);
            });
  }

  // --- Replication Control ---

  private static void pauseReplicaReplay() throws Exception {
    try (final Connection conn = replicaConnection();
        final Statement stmt = conn.createStatement()) {
      stmt.execute("SELECT pg_wal_replay_pause()");
    }
  }

  private static void resumeReplicaReplay() throws Exception {
    try (final Connection conn = replicaConnection();
        final Statement stmt = conn.createStatement()) {
      stmt.execute("SELECT pg_wal_replay_resume()");
    }
  }

  // --- Wait Helpers ---

  static void waitForReplication() {
    final String url =
        "jdbc:postgresql://" + primary.getHost() + ":" + primary.getMappedPort(5432) + "/testdb";
    System.out.println("[AsyncReplicationIT] Waiting for replication at " + url);

    int attempts = 0;
    while (attempts < 60) {
      try (final Connection conn = DriverManager.getConnection(url, "postgres", "secret");
          final Statement stmt = conn.createStatement();
          final ResultSet rs = stmt.executeQuery("SELECT count(*) FROM pg_stat_replication")) {
        if (rs.next() && rs.getInt(1) > 0) {
          System.out.println(
              "[AsyncReplicationIT] Replication established after " + attempts + " attempts");
          return;
        }
        System.out.println(
            "[AsyncReplicationIT] Attempt "
                + attempts
                + ": pg_stat_replication count = "
                + rs.getInt(1));
      } catch (final Exception e) {
        System.out.println("[AsyncReplicationIT] Attempt " + attempts + ": " + e.getMessage());
      }
      attempts++;
      try {
        Thread.sleep(2000);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    throw new RuntimeException("Replication not established after " + attempts + " attempts");
  }

  static void waitForReplicasInSync() {
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final Connection conn = primaryConnection();
                  final Statement stmt = conn.createStatement();
                  final ResultSet rs =
                      stmt.executeQuery(
                          "SELECT pg_current_wal_lsn() = replay_lsn"
                              + " FROM pg_stat_replication")) {
                while (rs.next()) {
                  if (!rs.getBoolean(1)) {
                    throw new AssertionError("Replicas are not in sync");
                  }
                }
              }
            });
  }

  // --- Connection Helpers ---

  static Connection primaryConnection() throws Exception {
    return DriverManager.getConnection(
        "jdbc:postgresql://" + primary.getHost() + ":" + primary.getMappedPort(5432) + "/testdb",
        "postgres",
        "secret");
  }

  static Connection replicaConnection() throws Exception {
    return DriverManager.getConnection(
        "jdbc:postgresql://" + replica.getHost() + ":" + replica.getMappedPort(5432) + "/testdb",
        "postgres",
        "secret");
  }
}
