/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
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
        super.stop();
      } finally {
        network.close();
      }
    }
  }

  public void stopReplica() {
    if (!replicaStopped) {
      LOG.info("Stopping PostgreSQL replica");
      replicaStopped = true;
      replica.stop();
      LOG.info("PostgreSQL replica stopped");
    }
  }

  public void startReplica() {
    LOG.info("Starting PostgreSQL replica");
    replicaStopped = false;
    replica.start();
    waitForReplication();
  }

  public String getJdbcUrl() {
    return "jdbc:postgresql://%s:%d/%s".formatted(getHost(), getMappedPort(5432), DATABASE_NAME);
  }

  public String getUsername() {
    return USERNAME;
  }

  public String getPassword() {
    return PASSWORD;
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
