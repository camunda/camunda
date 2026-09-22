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
  private static final Duration REPLICATION_TIMEOUT =
      Duration.ofMinutes(Long.getLong("test.oracle.replication.timeout.minutes", 45L));
  private static final Duration REPLICATION_POLL_INTERVAL = Duration.ofSeconds(30);
  private static final Logger LOG =
      LoggerFactory.getLogger(OracleDataGuardReplicationCluster.class);

  private final String jdbcUrl = requireEnv(ENV_JDBC_URL);
  private final String username = requireEnv(ENV_USERNAME);
  private final String password = requireEnv(ENV_PASSWORD);
  private final String stopReplicaCommand = requireEnv(ENV_STOP_REPLICA_CMD);
  private final String startReplicaCommand = requireEnv(ENV_START_REPLICA_CMD);

  /**
   * The Data Guard cluster is provisioned externally; starting this fixture verifies that the
   * standby has caught up with the primary before the test uses it.
   */
  @Override
  public void start() {
    waitForReplication(getCurrentScn());
  }

  /** The CI workflow owns the managed cluster lifecycle and cleans it up after the test. */
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
    LOG.info("Oracle Data Guard replica outage command completed");
    waitForReplicaUnavailable();
    LOG.info("Oracle Data Guard replica is unavailable");
    return CompletableFuture.completedFuture(Unit.unit());
  }

  @Override
  public Future<Void> startReplica() {
    final long recoveryScn = getCurrentScn();
    LOG.info("Starting the Oracle Data Guard replica");
    executeReplicaCommand(startReplicaCommand);
    LOG.info(
        "Oracle Data Guard replica recovery command completed; waiting for applied SCN {}",
        recoveryScn);
    waitForReplication(recoveryScn);
    LOG.info("Oracle Data Guard replica reached recovery SCN {}", recoveryScn);
    return CompletableFuture.completedFuture(Unit.unit());
  }

  private void waitForReplication(final long targetScn) {
    LOG.info(
        "Waiting up to {} for Oracle Data Guard replication to reach SCN {}",
        REPLICATION_TIMEOUT,
        targetScn);
    await()
        .atMost(REPLICATION_TIMEOUT)
        .pollInterval(REPLICATION_POLL_INTERVAL)
        .ignoreExceptions()
        .untilAsserted(() -> assertReplicationReached(targetScn));
  }

  private void waitForReplicaUnavailable() {
    LOG.info(
        "Waiting up to {} for the Oracle Data Guard replica to become unavailable",
        REPLICATION_TIMEOUT);
    await()
        .atMost(REPLICATION_TIMEOUT)
        .pollInterval(REPLICATION_POLL_INTERVAL)
        .ignoreExceptions()
        .untilAsserted(this::assertReplicaUnavailable);
  }

  private void assertReplicaUnavailable() throws SQLException {
    try (final Connection connection = openConnection();
        final Statement statement = connection.createStatement();
        final ResultSet resultSet =
            statement.executeQuery(
                """
                SELECT ad.dest_id,
                       ad.status AS destination_status,
                       ads.status AS destination_runtime_status,
                       ad.target
                FROM v$archive_dest ad
                JOIN v$archive_dest_status ads ON ads.dest_id = ad.dest_id
                WHERE ads.type IN ('PHYSICAL', 'LOGICAL')
                  AND ads.status = 'VALID'
                  AND ad.status = 'VALID'
                  AND ad.target IN ('STANDBY', 'REMOTE')
                """)) {
      if (resultSet.next()) {
        throw new AssertionError(
            "Oracle Data Guard replica is still available: destination="
                + resultSet.getString("dest_id")
                + ", target="
                + resultSet.getString("target")
                + ", destination status="
                + resultSet.getString("destination_status")
                + ", runtime status="
                + resultSet.getString("destination_runtime_status"));
      }
    } catch (final SQLException e) {
      LOG.warn("Unable to query Oracle Data Guard availability; will retry", e);
      throw e;
    }
  }

  private void assertReplicationReached(final long targetScn) throws SQLException {
    try (final Connection connection = openConnection();
        final Statement statement = connection.createStatement();
        final ResultSet resultSet =
            statement.executeQuery(
                """
                SELECT ad.dest_id,
                       ad.applied_scn,
                       ad.status AS destination_status,
                       ads.status AS destination_runtime_status,
                       ad.target
                FROM v$archive_dest ad
                JOIN v$archive_dest_status ads ON ads.dest_id = ad.dest_id
                WHERE ads.type IN ('PHYSICAL', 'LOGICAL')
                  AND ads.status = 'VALID'
                  AND ad.status = 'VALID'
                  AND ad.target IN ('STANDBY', 'REMOTE')
                """)) {
      boolean foundDestination = false;
      while (resultSet.next()) {
        foundDestination = true;
        final long appliedScn = resultSet.getLong("applied_scn");
        final boolean appliedScnWasNull = resultSet.wasNull();
        LOG.info(
            "Oracle Data Guard recovery status: destination={}, target={}, target SCN={}, "
                + "applied SCN={}, destination status={}, runtime status={}",
            resultSet.getString("dest_id"),
            resultSet.getString("target"),
            targetScn,
            appliedScn,
            resultSet.getString("destination_status"),
            resultSet.getString("destination_runtime_status"));
        if (!appliedScnWasNull && appliedScn >= targetScn) {
          return;
        }
      }
      if (!foundDestination) {
        throw new AssertionError("Oracle Data Guard has no valid standby destination");
      }
      throw new AssertionError("Oracle Data Guard replica has not reached SCN " + targetScn);
    } catch (final SQLException e) {
      LOG.warn("Unable to query Oracle Data Guard recovery status; will retry", e);
      throw e;
    }
  }

  private long getCurrentScn() {
    try (final Connection connection = openConnection();
        final Statement statement = connection.createStatement();
        final ResultSet resultSet = statement.executeQuery("SELECT current_scn FROM v$database")) {
      if (!resultSet.next()) {
        throw new IllegalStateException("Oracle did not return a current SCN");
      }
      return resultSet.getLong(1);
    } catch (final SQLException e) {
      throw new IllegalStateException("Unable to query Oracle current SCN", e);
    }
  }

  private Connection openConnection() throws SQLException {
    return DriverManager.getConnection(jdbcUrl, username, password);
  }

  private void executeReplicaCommand(final String command) {
    LOG.info("Executing Oracle Data Guard replica command: {}", command);
    Process process = null;
    try {
      process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
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
      if (process != null) {
        process.destroyForcibly();
      }
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
