/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.util.Unit;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Container which not only starts a PostgreSQL database but also a read replica.
 *
 * <p>This postgresql container uses the <i>postgres</i> DBA user to not rely on pg_monitor
 * privileges to be set up.
 */
@SuppressWarnings("resource")
public final class PostgresReplicationClusterContainer
    extends GenericContainer<PostgresReplicationClusterContainer>
    implements ReplicationClusterContainer {

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse("bitnamilegacy/postgresql").withTag("15");
  private static final String DATABASE_NAME = "camunda";
  private static final String USERNAME = "postgres";
  private static final String PASSWORD = "secret";
  private static final String REPLICATION_USER = "repl_user";
  private static final String REPLICATION_PASSWORD = "repl_pass";

  private static final Logger LOG =
      LoggerFactory.getLogger(PostgresReplicationClusterContainer.class);

  private final Network network = Network.newNetwork();
  private final GenericContainer<?> replica;
  private volatile boolean replicaStopped = false;
  private volatile boolean primaryStopped = false;

  public PostgresReplicationClusterContainer() {
    super(POSTGRES_IMAGE);

    withNetwork(network)
        .withNetworkAliases("primary")
        .withEnv("POSTGRESQL_REPLICATION_MODE", "master")
        .withEnv("POSTGRESQL_REPLICATION_USER", REPLICATION_USER)
        .withEnv("POSTGRESQL_REPLICATION_PASSWORD", REPLICATION_PASSWORD)
        .withEnv("POSTGRESQL_PASSWORD", PASSWORD)
        .withEnv("POSTGRESQL_DATABASE", DATABASE_NAME)
        .withExposedPorts(5432)
        .withStartupTimeout(Duration.ofMinutes(5));

    replica =
        new GenericContainer<>(POSTGRES_IMAGE)
            .withNetwork(network)
            .withEnv("POSTGRESQL_REPLICATION_MODE", "slave")
            .withEnv("POSTGRESQL_MASTER_HOST", "primary")
            .withEnv("POSTGRESQL_REPLICATION_USER", REPLICATION_USER)
            .withEnv("POSTGRESQL_REPLICATION_PASSWORD", REPLICATION_PASSWORD)
            .withEnv("POSTGRESQL_PASSWORD", PASSWORD)
            .withExposedPorts(5432)
            .withStartupTimeout(Duration.ofMinutes(5));
  }

  @Override
  public void start() {
    LOG.info("Starting PostgreSQL replication cluster (primary + replica)");
    super.start();
    replica.start();
    waitForReplication();
  }

  @Override
  public void stop() {
    LOG.info("Stopping PostgreSQL replication cluster");
    try {
      stopReplica();
    } finally {
      try {
        stopPrimary();
      } finally {
        network.close();
      }
    }
  }

  @Override
  public String getJdbcUrl() {
    return "jdbc:postgresql://%s:%d/%s".formatted(getHost(), getMappedPort(5432), DATABASE_NAME);
  }

  @Override
  public String getUsername() {
    return USERNAME;
  }

  @Override
  public String getPassword() {
    return PASSWORD;
  }

  @Override
  public Future<Void> stopReplica() {
    if (!replicaStopped) {
      LOG.info("Stopping PostgreSQL replica");
      replicaStopped = true;
      replica.stop();
      LOG.info("PostgreSQL replica stopped");
    }
    return CompletableFuture.completedFuture(Unit.unit());
  }

  @Override
  public Future<Void> startReplica() {
    LOG.info("Starting PostgreSQL replica");
    replicaStopped = false;
    replica.start();
    waitForReplication();
    return CompletableFuture.completedFuture(Unit.unit());
  }

  /** Host on which the replica's Postgres port is reachable from the test JVM. */
  public String getReplicaHost() {
    return replica.getHost();
  }

  /** Mapped host port for the replica's Postgres port (5432). */
  public int getReplicaPort() {
    return replica.getMappedPort(5432);
  }

  /**
   * A multi-host JDBC URL listing both the primary and the replica, with {@code
   * targetServerType=primary} so the PostgreSQL JDBC driver always routes new connections to
   * whichever listed host is currently writable.
   */
  public String getFailoverJdbcUrl() {
    return "jdbc:postgresql://%s:%d,%s:%d/%s?targetServerType=primary&connectTimeout=10&socketTimeout=15"
        .formatted(
            getHost(), getMappedPort(5432), getReplicaHost(), getReplicaPort(), DATABASE_NAME);
  }

  /**
   * Stops only the primary, leaving the replica (if still running) untouched. Used to simulate the
   * primary database going down during a failover.
   */
  public Future<Void> stopPrimary() {
    if (!primaryStopped) {
      LOG.info("Stopping PostgreSQL primary");
      primaryStopped = true;
      super.stop();
      LOG.info("PostgreSQL primary stopped");
    }
    return CompletableFuture.completedFuture(Unit.unit());
  }

  /**
   * Severs replication from the primary's side, so the replica stops receiving new WAL from this
   * point on, without stopping or restarting either container.
   */
  public void disconnectReplicaFromPrimary() {
    LOG.info("Disconnecting PostgreSQL replica from the primary");
    try (final Connection conn = primaryConnection();
        final Statement stmt = conn.createStatement()) {
      stmt.execute("ALTER ROLE " + REPLICATION_USER + " WITH NOLOGIN");
      stmt.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_replication");
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to disconnect PostgreSQL replica", e);
    }
    LOG.info("PostgreSQL replica disconnected from primary");
  }

  /**
   * Promotes the (still-running) replica to a standalone writable primary via {@code pg_promote()}.
   */
  public void promoteReplica() {
    LOG.info("Promoting PostgreSQL replica to primary");
    try (final Connection conn = replicaConnection();
        final Statement stmt = conn.createStatement();
        final ResultSet rs = stmt.executeQuery("SELECT pg_promote(true, 60)")) {
      rs.next();
      final boolean promoted = rs.getBoolean(1);
      if (!promoted) {
        throw new IllegalStateException("pg_promote() returned false, replica was not promoted");
      }
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to promote PostgreSQL replica", e);
    }
    LOG.info("PostgreSQL replica promoted to primary");
  }

  /**
   * Dynamically changes how long the standby waits before applying WAL commits from the primary.
   */
  public void setReplicaApplyDelay(final Duration delay) {
    if (delay.isNegative()) {
      throw new IllegalArgumentException("Replica apply delay must not be negative");
    }

    final var delayInMillis = delay.toMillis();
    LOG.info("Setting PostgreSQL replica apply delay to {} ms", delayInMillis);
    executeOnReplica("ALTER SYSTEM SET recovery_min_apply_delay = '%dms'".formatted(delayInMillis));
    reloadReplicaConfiguration();
  }

  public void resetReplicaApplyDelay() {
    LOG.info("Resetting PostgreSQL replica apply delay");
    executeOnReplica("ALTER SYSTEM RESET recovery_min_apply_delay");
    reloadReplicaConfiguration();
  }

  private Connection replicaConnection() throws Exception {
    return DriverManager.getConnection(
        "jdbc:postgresql://%s:%d/%s".formatted(getReplicaHost(), getReplicaPort(), DATABASE_NAME),
        USERNAME,
        PASSWORD);
  }

  private void reloadReplicaConfiguration() {
    executeOnReplica("SELECT pg_reload_conf()");
  }

  private void executeOnReplica(final String sql) {
    try (final Connection conn = replicaConnection();
        final Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to execute SQL on PostgreSQL replica: " + sql, e);
    }
  }

  private void waitForReplication() {
    LOG.info("Waiting for replica to connect");
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
                          """
                        SELECT count(*) FROM pg_stat_replication
                                        WHERE pid IS NOT NULL
                                          AND COALESCE(pg_wal_lsn_diff(replay_lsn, '0/0'), 0)::bigint > 0
                        """)) {

                rs.next();
                final int count = rs.getInt(1);
                if (count < 1) {
                  throw new AssertionError("Expected 1 replica, but found " + count);
                }
              }
            });
    LOG.info("Replica is in sync with primary");
  }

  private Connection primaryConnection() throws Exception {
    return DriverManager.getConnection(getJdbcUrl(), USERNAME, PASSWORD);
  }
}
