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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

/**
 * Container simulating a 2-region Postgres topology for region-aware replication quorum testing:
 * one primary (self-declaring {@value #REGION_A_PRIMARY_LABEL} via its {@code cluster_name},
 * matched the same way {@code getCurrentReplicaLabel()} resolves the primary's region in
 * production) plus three secondaries labeled by their own {@code application_name} - {@value
 * #REGION_A_NODE_1} in region A, {@value #REGION_B_NODE_1} and {@value #REGION_B_NODE_2} in region
 * B.
 *
 * <p>Unlike {@link PostgresReplicationClusterContainer}'s single replica, region membership here
 * needs a stable per-node label read from {@code pg_stat_replication.application_name}. The Bitnami
 * image's slave-mode bootstrap doesn't expose a way to set that at container creation, so each
 * secondary is relabeled right after it starts (and after every restart, since a stopped secondary
 * comes back with a fresh data directory and its default label again): its own {@code
 * primary_conninfo} is overwritten to include {@code application_name=<label>} and reloaded - the
 * same {@code ALTER SYSTEM SET ...; pg_reload_conf()} idiom {@link
 * PostgresReplicationClusterContainer#setReplicaApplyDelay} already uses for {@code
 * recovery_min_apply_delay} - which makes the WAL receiver reconnect under the new name. The
 * primary's {@code cluster_name}, by contrast, is a postmaster-context setting that can only take
 * effect at first start, so it's passed via {@code POSTGRESQL_EXTRA_FLAGS} instead.
 */
@SuppressWarnings("resource")
public final class PostgresRegionAwareReplicationClusterContainer
    extends GenericContainer<PostgresRegionAwareReplicationClusterContainer>
    implements ReplicationClusterContainer {

  public static final String REGION_A_PRIMARY_LABEL = "region-a-primary";
  public static final String REGION_A_NODE_1 = "region-a-1";
  public static final String REGION_B_NODE_1 = "region-b-1";
  public static final String REGION_B_NODE_2 = "region-b-2";

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse("bitnamilegacy/postgresql").withTag("15");
  private static final String DATABASE_NAME = "camunda";
  private static final String USERNAME = "postgres";
  private static final String PASSWORD = "secret";
  private static final String REPLICATION_USER = "repl_user";
  private static final String REPLICATION_PASSWORD = "repl_pass";

  private static final Logger LOG =
      LoggerFactory.getLogger(PostgresRegionAwareReplicationClusterContainer.class);

  private final Network network = Network.newNetwork();
  private final Map<String, GenericContainer<?>> secondariesByLabel = new LinkedHashMap<>();
  private final Set<String> stoppedLabels = ConcurrentHashMap.newKeySet();

  public PostgresRegionAwareReplicationClusterContainer() {
    super(POSTGRES_IMAGE);

    withNetwork(network)
        .withNetworkAliases("primary")
        .withEnv("POSTGRESQL_REPLICATION_MODE", "master")
        .withEnv("POSTGRESQL_REPLICATION_USER", REPLICATION_USER)
        .withEnv("POSTGRESQL_REPLICATION_PASSWORD", REPLICATION_PASSWORD)
        .withEnv("POSTGRESQL_PASSWORD", PASSWORD)
        .withEnv("POSTGRESQL_DATABASE", DATABASE_NAME)
        // cluster_name is postmaster-context (only takes effect at first start, unlike the
        // secondaries' application_name below, which is sighup-reloadable) - POSTGRESQL_EXTRA_FLAGS
        // is appended verbatim to the postgres server invocation by the Bitnami image's run.sh.
        .withEnv("POSTGRESQL_EXTRA_FLAGS", "-c cluster_name=" + REGION_A_PRIMARY_LABEL)
        .withExposedPorts(5432)
        .withStartupTimeout(Duration.ofMinutes(5));

    for (final String label : List.of(REGION_A_NODE_1, REGION_B_NODE_1, REGION_B_NODE_2)) {
      secondariesByLabel.put(label, newSecondary());
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
    LOG.info("Starting PostgreSQL region-aware replication cluster (primary + 3 secondaries)");
    super.start();
    secondariesByLabel.values().forEach(GenericContainer::start);
    secondariesByLabel.forEach(this::relabelSecondary);
    waitForReplication(secondariesByLabel.keySet());
  }

  @Override
  public void stop() {
    LOG.info("Stopping PostgreSQL region-aware replication cluster");
    try {
      for (final String label : secondariesByLabel.keySet()) {
        try {
          stopNodeInternal(label);
        } catch (final Exception e) {
          LOG.warn("Failed to stop secondary '{}' during cluster shutdown", label, e);
        }
      }
    } finally {
      try {
        super.stop();
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
    secondariesByLabel.keySet().forEach(this::stopNodeInternal);
    return CompletableFuture.completedFuture(Unit.unit());
  }

  @Override
  public Future<Void> startReplica() {
    secondariesByLabel.keySet().forEach(this::startNodeInternal);
    waitForReplication(secondariesByLabel.keySet());
    return CompletableFuture.completedFuture(Unit.unit());
  }

  /**
   * Stops a single named secondary (see the {@code REGION_*} label constants), leaving the rest
   * running.
   */
  public Future<Void> stopNode(final String label) {
    stopNodeInternal(label);
    return CompletableFuture.completedFuture(Unit.unit());
  }

  /** (Re-)starts a single named secondary and waits for it to rejoin under its region label. */
  public Future<Void> startNode(final String label) {
    startNodeInternal(label);
    waitForReplication(Set.of(label));
    return CompletableFuture.completedFuture(Unit.unit());
  }

  private void stopNodeInternal(final String label) {
    if (stoppedLabels.add(label)) {
      LOG.info("Stopping PostgreSQL secondary '{}'", label);
      secondaryFor(label).stop();
    }
  }

  private void startNodeInternal(final String label) {
    LOG.info("Starting PostgreSQL secondary '{}'", label);
    final GenericContainer<?> secondary = secondaryFor(label);
    secondary.start();
    relabelSecondary(label, secondary);
    stoppedLabels.remove(label);
  }

  private GenericContainer<?> secondaryFor(final String label) {
    final GenericContainer<?> secondary = secondariesByLabel.get(label);
    if (secondary == null) {
      throw new IllegalArgumentException("Unknown secondary label: " + label);
    }
    return secondary;
  }

  /**
   * Overwrites the secondary's own {@code primary_conninfo} to include {@code
   * application_name=<label>} and reloads, so its WAL receiver reconnects under that name - see the
   * class Javadoc. Retries: right after {@code start()} returns, the container may not yet be
   * accepting SQL connections even though its exposed port is already listening - on a restart
   * (rather than the very first start), Bitnami re-runs its full bootstrap (recreating the
   * replication user, re-seeding via {@code pg_basebackup}) before Postgres is reachable at all,
   * which can take substantially longer than a fresh container's first boot; the retry window here
   * matches {@link #waitForReplication}'s.
   */
  private void relabelSecondary(final String label, final GenericContainer<?> secondary) {
    final String conninfo =
        "host=primary port=5432 user=%s password=%s application_name=%s"
            .formatted(REPLICATION_USER, REPLICATION_PASSWORD, label);
    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              executeOn(secondary, "ALTER SYSTEM SET primary_conninfo = '" + conninfo + "'");
              executeOn(secondary, "SELECT pg_reload_conf()");
            });
  }

  private void executeOn(final GenericContainer<?> container, final String sql) {
    try (final Connection conn = connectionTo(container);
        final Statement stmt = conn.createStatement()) {
      stmt.execute(sql);
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to execute SQL on PostgreSQL secondary: " + sql, e);
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

  /**
   * Waits until every given label shows up in {@code pg_stat_replication} on the primary, in sync.
   */
  private void waitForReplication(final Set<String> labels) {
    LOG.info("Waiting for secondaries {} to (re)connect under their region label", labels);
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
    LOG.info("Secondaries {} are in sync with primary under their region label", labels);
  }
}
