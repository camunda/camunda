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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ReplicationClusterContainer} backed by an RDS for Oracle primary and Data Guard replica.
 *
 * <p>The cluster is provisioned by the Oracle async-replication CI workflow. The primary JDBC URL,
 * credentials, and AWS CLI commands that stop and start the replica are supplied through
 * environment variables so that the test can reuse the same lifecycle as the Aurora acceptance
 * tests without embedding AWS-specific infrastructure code in the test process.
 */
public final class OracleDataGuardReplicationCluster implements ReplicationClusterContainer {

  public static final String ENV_JDBC_URL = "TEST_ORACLE_JDBC_URL";
  public static final String ENV_USERNAME = "TEST_ORACLE_USERNAME";
  public static final String ENV_PASSWORD = "TEST_ORACLE_PASSWORD";
  public static final String ENV_STOP_REPLICA_CMD = "TEST_ORACLE_STOP_REPLICA_CMD";
  public static final String ENV_START_REPLICA_CMD = "TEST_ORACLE_START_REPLICA_CMD";

  private static final Duration REPLICA_COMMAND_TIMEOUT = Duration.ofMinutes(30);
  private static final Duration REPLICATION_TIMEOUT = Duration.ofMinutes(45);
  private static final Duration REPLICATION_POLL_INTERVAL = Duration.ofSeconds(30);
  private static final Logger LOG =
      LoggerFactory.getLogger(OracleDataGuardReplicationCluster.class);

  private final String jdbcUrl = requireEnv(ENV_JDBC_URL);
  private final String username = requireEnv(ENV_USERNAME);
  private final String password = requireEnv(ENV_PASSWORD);
  private final String stopReplicaCommand = requireEnv(ENV_STOP_REPLICA_CMD);
  private final String startReplicaCommand = requireEnv(ENV_START_REPLICA_CMD);

  @Override
  public void start() {
    LOG.info("Waiting for the Oracle Data Guard replica to report applied SCN progress");
    waitForReplication();
  }

  @Override
  public void stop() {}

  @Override
  public String getJdbcUrl() {
    return jdbcUrl;
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
  public Future<Void> stopReplica() {
    LOG.info("Stopping the Oracle Data Guard replica");
    executeReplicaCommand(stopReplicaCommand);
    return CompletableFuture.completedFuture(Unit.unit());
  }

  @Override
  public Future<Void> startReplica() {
    LOG.info("Starting the Oracle Data Guard replica");
    executeReplicaCommand(startReplicaCommand);
    waitForReplication();
    return CompletableFuture.completedFuture(Unit.unit());
  }

  private void waitForReplication() {
    await()
        .atMost(REPLICATION_TIMEOUT)
        .pollInterval(REPLICATION_POLL_INTERVAL)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final Connection connection = openConnection();
                  final Statement statement = connection.createStatement();
                  final ResultSet resultSet =
                      statement.executeQuery(
                          """
                          SELECT ad.dest_id, ad.applied_scn
                          FROM v$archive_dest ad
                          JOIN v$archive_dest_status ads ON ads.dest_id = ad.dest_id
                          WHERE ads.type IN ('PHYSICAL', 'LOGICAL')
                            AND ads.status = 'VALID'
                            AND ad.status = 'VALID'
                            AND ad.target IN ('STANDBY', 'REMOTE')
                            AND ad.applied_scn > 0
                          """)) {
                if (!hasAppliedScn(resultSet)) {
                  throw new AssertionError("Oracle Data Guard has not reported an applied SCN");
                }
              }
            });
    LOG.info("Oracle Data Guard replica is reporting applied SCN progress");
  }

  private boolean hasAppliedScn(final ResultSet resultSet) throws SQLException {
    while (resultSet.next()) {
      final long appliedScn = resultSet.getLong("applied_scn");
      LOG.debug(
          "Oracle Data Guard destination {} reports applied SCN {}",
          resultSet.getString("dest_id"),
          appliedScn);
      if (!resultSet.wasNull() && appliedScn > 0) {
        return true;
      }
    }
    return false;
  }

  private Connection openConnection() throws SQLException {
    return DriverManager.getConnection(jdbcUrl, username, password);
  }

  private void executeReplicaCommand(final String command) {
    LOG.info("Executing Oracle Data Guard replica command: {}", command);
    try {
      final Process process =
          new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
      final boolean finished =
          process.waitFor(REPLICA_COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("Oracle Data Guard replica command timed out: " + command);
      }
      final String output;
      try (final var input = process.getInputStream()) {
        output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      }
      final int exitCode = process.exitValue();
      LOG.info("Oracle Data Guard replica command output:\n{}", output);
      if (exitCode != 0) {
        throw new IllegalStateException(
            "Oracle Data Guard replica command failed with exit code %d: %s"
                .formatted(exitCode, command));
      }
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to run Oracle Data Guard replica command", e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while running Oracle Data Guard replica command", e);
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
