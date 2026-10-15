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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Container which starts a PostgreSQL primary and one or more read replicas.
 *
 * <p>The no-argument constructor preserves the original single-replica topology. A custom list of
 * replica labels can be supplied for tests that need multiple independently addressable replicas;
 * each label is configured as the replica's PostgreSQL {@code application_name}. The optional
 * primary label is configured as PostgreSQL's {@code cluster_name}.
 *
 * <p>This PostgreSQL container uses the <i>postgres</i> DBA user to not rely on pg_monitor
 * privileges to be set up.
 */
@SuppressWarnings("resource")
public final class PostgresReplicationClusterContainer
    extends GenericContainer<PostgresReplicationClusterContainer>
    implements ReplicationClusterContainer {

  private static final String DEFAULT_REPLICA_LABEL = "replica";
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
  private final Map<String, GenericContainer<?>> replicasByLabel = new LinkedHashMap<>();
  private final Set<String> stoppedLabels = ConcurrentHashMap.newKeySet();
  private volatile boolean primaryStopped = false;

  public PostgresReplicationClusterContainer() {
    this(List.of(DEFAULT_REPLICA_LABEL), null);
  }

  /**
   * Creates a PostgreSQL replication cluster with the given replica labels and optional primary
   * label.
   *
   * @param replicaLabels labels used as the replicas' PostgreSQL {@code application_name}; labels
   *     must be unique and non-blank
   * @param primaryLabel the primary's PostgreSQL {@code cluster_name}, or {@code null} when no
   *     primary label is needed
   */
  public PostgresReplicationClusterContainer(
      final List<String> replicaLabels, final String primaryLabel) {
    super(POSTGRES_IMAGE);

    if (replicaLabels.isEmpty()
        || replicaLabels.stream().anyMatch(label -> label == null || label.isBlank())
        || replicaLabels.stream().distinct().count() != replicaLabels.size()) {
      throw new IllegalArgumentException("Replica labels must be unique and non-blank");
    }

    withNetwork(network)
        .withNetworkAliases("primary")
        .withEnv("POSTGRESQL_REPLICATION_MODE", "master")
        .withEnv("POSTGRESQL_REPLICATION_USER", REPLICATION_USER)
        .withEnv("POSTGRESQL_REPLICATION_PASSWORD", REPLICATION_PASSWORD)
        .withEnv("POSTGRESQL_PASSWORD", PASSWORD)
        .withEnv("POSTGRESQL_DATABASE", DATABASE_NAME)
        .withExposedPorts(5432)
        .withStartupTimeout(Duration.ofMinutes(5));

    if (primaryLabel != null) {
      withEnv("POSTGRESQL_EXTRA_FLAGS", "-c cluster_name=" + primaryLabel);
    }

    for (final String label : replicaLabels) {
      replicasByLabel.put(label, newSecondary());
    }
  }

  private GenericContainer<?> newSecondary() {
    return new GenericContainer<>(POSTGRES_IMAGE)
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
    LOG.info(
        "Starting PostgreSQL replication cluster (primary + {} secondaries)",
        replicasByLabel.size());
    super.start();
    replicasByLabel.values().forEach(GenericContainer::start);
    replicasByLabel.forEach(this::relabelSecondary);
    waitForReplication(replicasByLabel.keySet());
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
    replicasByLabel.keySet().forEach(this::stopNodeInternal);
    return CompletableFuture.completedFuture(Unit.unit());
  }

  @Override
  public Future<Void> startReplica() {
    replicasByLabel.keySet().forEach(this::startNodeInternal);
    waitForReplication(replicasByLabel.keySet());
    return CompletableFuture.completedFuture(Unit.unit());
  }

  /** Stops a named replica, leaving the primary and other replicas running. */
  public Future<Void> stopNode(final String label) {
    stopNodeInternal(label);
    return CompletableFuture.completedFuture(Unit.unit());
  }

  /** Starts a named replica and waits for it to rejoin under its configured label. */
  public Future<Void> startNode(final String label) {
    startNodeInternal(label);
    waitForReplication(Set.of(label));
    return CompletableFuture.completedFuture(Unit.unit());
  }

  /** Host on which the first replica's Postgres port is reachable from the test JVM. */
  public String getReplicaHost() {
    return firstReplica().getHost();
  }

  /** Mapped host port for the first replica's Postgres port (5432). */
  public int getReplicaPort() {
    return firstReplica().getMappedPort(5432);
  }

  /**
   * A multi-host JDBC URL listing the primary and all replicas, with {@code
   * targetServerType=primary} so the PostgreSQL JDBC driver always routes new connections to
   * whichever listed host is currently writable.
   */
  public String getFailoverJdbcUrl() {
    final String replicaHosts =
        replicasByLabel.values().stream()
            .map(replica -> "%s:%d".formatted(replica.getHost(), replica.getMappedPort(5432)))
            .collect(Collectors.joining(","));
    return "jdbc:postgresql://%s:%d,%s/%s?targetServerType=primary&connectTimeout=10&socketTimeout=15"
        .formatted(getHost(), getMappedPort(5432), replicaHosts, DATABASE_NAME);
  }

  /**
   * Stops only the primary, leaving the replicas (if still running) untouched. Used to simulate the
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
   * Severs replication from the primary's side, so the replicas stop receiving new WAL from this
   * point on, without stopping or restarting any container.
   */
  public void disconnectReplicaFromPrimary() {
    LOG.info("Disconnecting PostgreSQL replicas from the primary");
    try (final Connection conn = primaryConnection();
        final Statement stmt = conn.createStatement()) {
      stmt.execute("ALTER ROLE " + REPLICATION_USER + " WITH NOLOGIN");
      stmt.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_replication");
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to disconnect PostgreSQL replicas", e);
    }
    LOG.info("PostgreSQL replicas disconnected from primary");
  }

  /** Promotes the first (and, in the original topology, only) replica to a writable primary. */
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

  /** Dynamically changes how long the first standby waits before applying WAL commits. */
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

  private void stopNodeInternal(final String label) {
    if (stoppedLabels.add(label)) {
      LOG.info("Stopping PostgreSQL replica '{}'", label);
      replicaFor(label).stop();
    }
  }

  private void startNodeInternal(final String label) {
    LOG.info("Starting PostgreSQL replica '{}'", label);
    final GenericContainer<?> replica = replicaFor(label);
    if (!replica.isRunning()) {
      replica.start();
      relabelSecondary(label, replica);
    }
    stoppedLabels.remove(label);
  }

  private GenericContainer<?> firstReplica() {
    return replicasByLabel.values().stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No PostgreSQL replicas are configured"));
  }

  private GenericContainer<?> replicaFor(final String label) {
    final GenericContainer<?> replica = replicasByLabel.get(label);
    if (replica == null) {
      throw new IllegalArgumentException("Unknown replica label: " + label);
    }
    return replica;
  }

  /**
   * Overwrites a replica's {@code primary_conninfo} to include its configured {@code
   * application_name} and reloads, so its WAL receiver reconnects under the new label. Retries
   * because the container may not yet be accepting SQL connections after startup or restart.
   */
  private void relabelSecondary(final String label, final GenericContainer<?> replica) {
    final String conninfo =
        "host=primary port=5432 user=%s password=%s application_name=%s"
            .formatted(REPLICATION_USER, REPLICATION_PASSWORD, label);
    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              executeOn(replica, "ALTER SYSTEM SET primary_conninfo = '" + conninfo + "'");
              executeOn(replica, "SELECT pg_reload_conf()");
            });
  }

  private void executeOn(final GenericContainer<?> container, final String sql) {
    try (final Connection conn = connectionTo(container);
        final Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to execute SQL on PostgreSQL replica: " + sql, e);
    }
  }

  private Connection connectionTo(final GenericContainer<?> container) throws Exception {
    return DriverManager.getConnection(
        "jdbc:postgresql://%s:%d/postgres"
            .formatted(container.getHost(), container.getMappedPort(5432)),
        USERNAME,
        PASSWORD);
  }

  private Connection primaryConnection() throws Exception {
    return DriverManager.getConnection(getJdbcUrl(), USERNAME, PASSWORD);
  }

  /** Waits until every configured label shows up on the primary and is in sync. */
  private void waitForReplication(final Set<String> labels) {
    LOG.info("Waiting for replicas {} to (re)connect under their configured labels", labels);
    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final Connection conn = primaryConnection();
                  final Statement stmt = conn.createStatement();
                  final ResultSet rs =
                      stmt.executeQuery(
                          """
                          SELECT application_name FROM pg_stat_replication
                                          WHERE pid IS NOT NULL
                                            AND COALESCE(pg_wal_lsn_diff(replay_lsn, '0/0'), 0)::bigint > 0
                          """)) {
                final Set<String> connected = new HashSet<>();
                while (rs.next()) {
                  connected.add(rs.getString(1));
                }
                if (!connected.containsAll(labels)) {
                  throw new AssertionError(
                      "Expected labels %s to be connected, but found %s"
                          .formatted(labels, connected));
                }
              }
            });
    LOG.info("Replicas {} are in sync with primary under their configured labels", labels);
  }

  private Connection replicaConnection() throws Exception {
    final GenericContainer<?> replica = firstReplica();
    return DriverManager.getConnection(
        "jdbc:postgresql://%s:%d/%s"
            .formatted(replica.getHost(), replica.getMappedPort(5432), DATABASE_NAME),
        USERNAME,
        PASSWORD);
  }

  private void executeOnReplica(final String sql) {
    try (final Connection conn = replicaConnection();
        final Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to execute SQL on PostgreSQL replica: " + sql, e);
    }
  }

  private void reloadReplicaConfiguration() {
    executeOnReplica("SELECT pg_reload_conf()");
  }
}
