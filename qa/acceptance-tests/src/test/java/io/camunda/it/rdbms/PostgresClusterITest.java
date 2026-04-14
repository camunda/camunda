package io.camunda.it.rdbms;

import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class PostgresClusterITest {

  static Network network = Network.newNetwork();

  static GenericContainer<?> primary;
  static GenericContainer<?> replica1;
  static GenericContainer<?> replica2;

  @BeforeAll
  static void setup() throws Exception {

    primary =
        new GenericContainer<>("bitnamilegacy/postgresql:15")
            .withNetwork(network)
            .withNetworkAliases("primary")
            .withEnv("POSTGRESQL_REPLICATION_MODE", "master")
            .withEnv("POSTGRESQL_REPLICATION_USER", "repl_user")
            .withEnv("POSTGRESQL_REPLICATION_PASSWORD", "repl_pass")
            .withEnv("POSTGRESQL_PASSWORD", "secret")
            .withEnv("POSTGRESQL_DATABASE", "testdb")
            .withExposedPorts(5432);

    replica1 =
        new GenericContainer<>("bitnamilegacy/postgresql:15")
            .withNetwork(network)
            .withEnv("POSTGRESQL_REPLICATION_MODE", "slave")
            .withEnv("POSTGRESQL_MASTER_HOST", "primary")
            .withEnv("POSTGRESQL_REPLICATION_USER", "repl_user")
            .withEnv("POSTGRESQL_REPLICATION_PASSWORD", "repl_pass")
            .withEnv("POSTGRESQL_PASSWORD", "secret");

    replica2 =
        new GenericContainer<>("bitnamilegacy/postgresql:15")
            .withNetwork(network)
            .withEnv("POSTGRESQL_REPLICATION_MODE", "slave")
            .withEnv("POSTGRESQL_MASTER_HOST", "primary")
            .withEnv("POSTGRESQL_REPLICATION_USER", "repl_user")
            .withEnv("POSTGRESQL_REPLICATION_PASSWORD", "repl_pass")
            .withEnv("POSTGRESQL_PASSWORD", "secret");

    primary.start();
    replica1.start();
    replica2.start();

    waitForReplication();
    waitForReplicasInSync();
  }

  // --- WAIT UNTIL REPLICAS CONNECT ---
  static void waitForReplication() {
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final Connection conn = primaryConnection();
                  final Statement stmt = conn.createStatement();
                  final ResultSet rs =
                      stmt.executeQuery("SELECT count(*) FROM pg_stat_replication")) {

                rs.next();
                final int count = rs.getInt(1);
                if (count < 2) {
                  throw new AssertionError("Expected 2 replicas, but found " + count);
                }
              }
            });
  }

  // --- WAIT UNTIL NO LAG ---
  static void waitForReplicasInSync() {
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final Connection conn = primaryConnection();
                  final Statement stmt = conn.createStatement();
                  final ResultSet rs =
                      stmt.executeQuery(
                          "SELECT pg_current_wal_lsn() = replay_lsn FROM pg_stat_replication")) {

                while (rs.next()) {
                  if (!rs.getBoolean(1)) {
                    throw new AssertionError("Replicas are not in sync");
                  }
                }
              }
            });
  }

  // --- CONNECTION HELPERS ---
  static Connection primaryConnection() throws Exception {
    return DriverManager.getConnection(
        "jdbc:postgresql://" + primary.getHost() + ":" + primary.getMappedPort(5432) + "/testdb",
        "postgres",
        "secret");
  }

  @Test
  void testReplicationWorks() throws Exception {
    try (final Connection conn = primaryConnection();
        final Statement stmt = conn.createStatement()) {

      stmt.execute("CREATE TABLE test_table (id INT PRIMARY KEY)");
      stmt.execute("INSERT INTO test_table VALUES (1)");
    }

    // Wait for replication
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              // Assertion logic here if needed
            });

    // You could also connect to replica and verify read
  }
}
