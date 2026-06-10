/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ReplicationClusterContainer} backed by a pre-provisioned AWS Aurora Global Database
 * instead of local containers.
 *
 * <p>An Aurora cluster cannot be spawned in CI; it is assumed to be set up beforehand and reachable
 * via the following environment variables:
 *
 * <ul>
 *   <li>{@value #ENV_JDBC_URL} – JDBC URL of the primary cluster endpoint (any maintenance
 *       database, e.g. {@code jdbc:postgresql://<endpoint>:5432/postgres})
 *   <li>{@value #ENV_USERNAME} – database user with {@code CREATEDB} privilege
 *   <li>{@value #ENV_PASSWORD} – password of that user
 *   <li>{@value #ENV_STOP_REPLICA_CMD} – shell command that removes the replica (e.g. a {@code
 *       terraform apply} scaling the secondary instance count to 0)
 *   <li>{@value #ENV_START_REPLICA_CMD} – shell command that restores the replica
 * </ul>
 *
 * <p>{@link #start()} creates a uniquely named scratch database on the cluster so each test run is
 * isolated; {@link #stop()} drops it again, leaving the cluster in a clean state. Replica removal
 * and recovery are delegated to the infrastructure-provided commands, since a managed Aurora Global
 * Database cannot be manipulated directly from a test; after {@link #startReplica()} the cluster
 * waits until the replica reports a position via {@code aurora_global_db_instance_status()}.
 */
public final class AuroraReplicationCluster implements ReplicationClusterContainer {

  public static final String ENV_JDBC_URL = "TEST_AURORA_JDBC_URL";
  public static final String ENV_USERNAME = "TEST_AURORA_USERNAME";
  public static final String ENV_PASSWORD = "TEST_AURORA_PASSWORD";
  public static final String ENV_STOP_REPLICA_CMD = "TEST_AURORA_STOP_REPLICA_CMD";
  public static final String ENV_START_REPLICA_CMD = "TEST_AURORA_START_REPLICA_CMD";

  private static final Duration REPLICA_COMMAND_TIMEOUT = Duration.ofMinutes(30);

  private static final Logger LOG = LoggerFactory.getLogger(AuroraReplicationCluster.class);

  private final String adminJdbcUrl = requireEnv(ENV_JDBC_URL);
  private final String username = requireEnv(ENV_USERNAME);
  private final String password = requireEnv(ENV_PASSWORD);
  private final String stopReplicaCommand = requireEnv(ENV_STOP_REPLICA_CMD);
  private final String startReplicaCommand = requireEnv(ENV_START_REPLICA_CMD);
  private final String databaseName = "camunda_it_" + UUID.randomUUID().toString().replace("-", "");

  @Override
  public void start() {
    LOG.info("Creating scratch database '{}' on Aurora cluster", databaseName);
    executeAdminStatement("CREATE DATABASE " + databaseName);
  }

  @Override
  public void stop() {
    LOG.info("Dropping scratch database '{}' on Aurora cluster", databaseName);
    executeAdminStatement("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
  }

  @Override
  public String getJdbcUrl() {
    // replace the maintenance database in the admin URL with the scratch database
    final int lastSlash = adminJdbcUrl.lastIndexOf('/');
    final int queryStart = adminJdbcUrl.indexOf('?', lastSlash);
    final String query = queryStart < 0 ? "" : adminJdbcUrl.substring(queryStart);
    return adminJdbcUrl.substring(0, lastSlash + 1) + databaseName + query;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public void stopReplica() {
    LOG.info("Removing Aurora replica via external command");
    executeReplicaCommand(stopReplicaCommand);
    LOG.info("Aurora replica removed");
  }

  @Override
  public void startReplica() {
    LOG.info("Restoring Aurora replica via external command");
    executeReplicaCommand(startReplicaCommand);
    waitForReplication();
    LOG.info("Aurora replica is in sync with primary");
  }

  private void executeReplicaCommand(final String command) {
    LOG.info("Executing replica command: {}", command);
    try {
      final Process process =
          new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
      final String output;
      try (final InputStream is = process.getInputStream()) {
        output = new String(is.readAllBytes());
      }
      final boolean finished =
          process.waitFor(REPLICA_COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        LOG.error("Replica command timed out. Output:\n{}", output);
        throw new IllegalStateException("Replica command timed out: " + command);
      }
      final int exitCode = process.exitValue();
      LOG.info("Replica command finished with exit code {}. Output:\n{}", exitCode, output);
      if (exitCode != 0) {
        throw new IllegalStateException(
            "Replica command failed with exit code %d: %s".formatted(exitCode, command));
      }
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to run replica command: " + command, e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while running replica command: " + command, e);
    }
  }

  /** Waits until a replica instance reports a replicated position on the primary endpoint. */
  private void waitForReplication() {
    await()
        .atMost(Duration.ofMinutes(45))
        .pollInterval(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final Connection connection =
                      DriverManager.getConnection(adminJdbcUrl, username, password);
                  final Statement statement = connection.createStatement();
                  final ResultSet rs =
                      statement.executeQuery(
                          """
                          SELECT session_id, durable_lsn FROM aurora_global_db_instance_status()
                           WHERE session_id <> 'MASTER_SESSION_ID'
                          """)) {
                int replicaCount = 0;
                while (rs.next()) {
                  replicaCount++;
                  LOG.info(
                      "Replica status: session_id={}, durable_lsn={}",
                      rs.getString("session_id"),
                      rs.getLong("durable_lsn"));
                  if (rs.getLong("durable_lsn") > 0) {
                    return;
                  }
                }
                throw new AssertionError(
                    "No replica instance reporting a durable_lsn yet (replicas visible: "
                        + replicaCount
                        + ")");
              }
            });
  }

  private void executeAdminStatement(final String sql) {
    try (final Connection connection =
            DriverManager.getConnection(adminJdbcUrl, username, password);
        final Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to execute on Aurora cluster: " + sql, e);
    }
  }

  private static String requireEnv(final String name) {
    final String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required environment variable: " + name);
    }
    return value;
  }
}
