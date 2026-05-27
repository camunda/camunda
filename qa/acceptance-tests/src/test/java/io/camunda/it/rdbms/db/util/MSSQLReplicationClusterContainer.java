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
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.testcontainers.utility.MountableFile;

/**
 * Container which starts an MSSQL primary and a read replica using Always On Availability Groups
 * (AG) with CLUSTER_TYPE=NONE — the Docker-friendly AG mode that needs no Windows cluster.
 *
 * <p>Replication status can be monitored via {@code sys.dm_hadr_database_replica_states} on the
 * primary, which is what {@code ReplicationStatusMapper} queries with {@code databaseId="mssql"}.
 */
@SuppressWarnings("resource")
public final class MSSQLReplicationClusterContainer
    extends GenericContainer<MSSQLReplicationClusterContainer>
    implements ReplicationClusterContainer {

  private static final DockerImageName MSSQL_IMAGE =
      DockerImageName.parse("mcr.microsoft.com/mssql/server").withTag("2022-latest");
  private static final String DATABASE_NAME = "camunda";
  private static final String SA_USER = "sa";
  private static final String SA_PASSWORD = "Strong_Pass123!";
  private static final String MASTER_KEY_PASSWORD = "MasterKey1234!";
  private static final String CERT_KEY_PASSWORD = "CertKey1234!";
  private static final String AG_NAME = "camunda_ag";
  // /tmp is world-writable so both the mssql process and testcontainers (root) can access it
  private static final String CERT_CONTAINER_PATH = "/tmp/dbm_certificate.cer";
  private static final String PVK_CONTAINER_PATH = "/tmp/dbm_certificate.pvk";
  private static final int MSSQL_PORT = 1433;

  private static final Logger LOG = LoggerFactory.getLogger(MSSQLReplicationClusterContainer.class);

  private final Network network = Network.newNetwork();
  private final GenericContainer<?> replica;
  private volatile boolean replicaStopped = false;

  // cached after first setup so they can be re-injected on replica restart
  private byte[] certFileBytes;
  private byte[] pvkFileBytes;

  public MSSQLReplicationClusterContainer() {
    super(MSSQL_IMAGE);

    // SQL Server uses @@SERVERNAME (= OS hostname) to identify AG members
    withNetwork(network)
        .withNetworkAliases("primary")
        .withCreateContainerCmdModifier(cmd -> cmd.withHostName("primary"))
        .withEnv("ACCEPT_EULA", "Y")
        .withEnv("MSSQL_SA_PASSWORD", SA_PASSWORD)
        .withEnv("MSSQL_ENABLE_HADR", "1")
        .withEnv("MSSQL_AGENT_ENABLED", "true")
        .withExposedPorts(MSSQL_PORT)
        .withStartupTimeout(Duration.ofMinutes(5));

    replica =
        new GenericContainer<>(MSSQL_IMAGE)
            .withNetwork(network)
            .withNetworkAliases("replica")
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("replica"))
            .withEnv("ACCEPT_EULA", "Y")
            .withEnv("MSSQL_SA_PASSWORD", SA_PASSWORD)
            .withEnv("MSSQL_ENABLE_HADR", "1")
            .withEnv("MSSQL_AGENT_ENABLED", "true")
            .withExposedPorts(MSSQL_PORT)
            .withStartupTimeout(Duration.ofMinutes(5));
  }

  @Override
  public void start() {
    LOG.info("Starting MSSQL replication cluster (primary + replica)");
    super.start();
    waitForSqlServerReady(primaryMasterJdbcUrl());
    initPrimary();
    cacheCertFiles();
    replica.start();
    waitForSqlServerReady(replicaMasterJdbcUrl());
    transferCertsToReplica();
    initReplica();
    waitForReplication();
    LOG.info("MSSQL replication cluster started");
  }

  @Override
  public void stop() {
    LOG.info("Stopping MSSQL replication cluster");
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
      LOG.info("Stopping MSSQL replica");
      replicaStopped = true;
      replica.stop();
      LOG.info("MSSQL replica stopped");
    }
  }

  public void startReplica() {
    LOG.info("Starting MSSQL replica");
    replicaStopped = false;
    replica.start();
    waitForSqlServerReady(replicaMasterJdbcUrl());
    transferCertsToReplica();
    initReplica();
    waitForReplication();
  }

  public String getJdbcUrl() {
    return buildJdbcUrl(getHost(), getMappedPort(MSSQL_PORT), DATABASE_NAME);
  }

  public String getUsername() {
    return SA_USER;
  }

  public String getPassword() {
    return SA_PASSWORD;
  }

  private void waitForSqlServerReady(final String jdbcUrl) {
    LOG.info("Waiting for SQL Server to be ready");
    await()
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .until(
            () -> {
              try (final Connection conn =
                      DriverManager.getConnection(jdbcUrl, SA_USER, SA_PASSWORD);
                  final Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                return true;
              }
            });
    LOG.info("SQL Server is ready");
  }

  private void initPrimary() {
    LOG.info("Initializing MSSQL primary");
    try (final Connection conn =
            DriverManager.getConnection(primaryMasterJdbcUrl(), SA_USER, SA_PASSWORD);
        final Statement stmt = conn.createStatement()) {

      stmt.execute("CREATE DATABASE [" + DATABASE_NAME + "]");
      stmt.execute("ALTER DATABASE [" + DATABASE_NAME + "] SET RECOVERY FULL");
      // A full backup is required to establish the log-backup chain before joining an AG,
      // even when SEEDING_MODE=AUTOMATIC handles the actual replica seeding.
      stmt.execute(
          "BACKUP DATABASE [" + DATABASE_NAME + "] TO DISK = N'/tmp/camunda_init.bak' WITH INIT");
      stmt.execute("CREATE MASTER KEY ENCRYPTION BY PASSWORD = '" + MASTER_KEY_PASSWORD + "'");
      stmt.execute("CREATE CERTIFICATE dbm_certificate WITH SUBJECT = 'dbm'");
      stmt.execute(
          """
          BACKUP CERTIFICATE dbm_certificate
            TO FILE = '%s'
            WITH PRIVATE KEY (
              FILE = '%s',
              ENCRYPTION BY PASSWORD = '%s'
            )
          """
              .formatted(CERT_CONTAINER_PATH, PVK_CONTAINER_PATH, CERT_KEY_PASSWORD));
      stmt.execute(
          """
          CREATE ENDPOINT [Hadr_endpoint]
            STATE = STARTED
            AS TCP (LISTENER_PORT = 5022)
            FOR DATABASE_MIRRORING (
              ROLE = ALL,
              AUTHENTICATION = CERTIFICATE dbm_certificate,
              ENCRYPTION = REQUIRED ALGORITHM AES
            )
          """);
      stmt.execute(
          """
          CREATE AVAILABILITY GROUP [%s]
            WITH (CLUSTER_TYPE = NONE)
            FOR DATABASE [%s]
            REPLICA ON
              N'primary' WITH (
                ENDPOINT_URL = N'tcp://primary:5022',
                FAILOVER_MODE = MANUAL,
                AVAILABILITY_MODE = ASYNCHRONOUS_COMMIT,
                SEEDING_MODE = AUTOMATIC,
                SECONDARY_ROLE (ALLOW_CONNECTIONS = ALL)
              ),
              N'replica' WITH (
                ENDPOINT_URL = N'tcp://replica:5022',
                FAILOVER_MODE = MANUAL,
                AVAILABILITY_MODE = ASYNCHRONOUS_COMMIT,
                SEEDING_MODE = AUTOMATIC,
                SECONDARY_ROLE (ALLOW_CONNECTIONS = ALL)
              )
          """
              .formatted(AG_NAME, DATABASE_NAME));
      stmt.execute("ALTER AVAILABILITY GROUP [" + AG_NAME + "] GRANT CREATE ANY DATABASE");

    } catch (final Exception e) {
      throw new RuntimeException("Failed to initialize MSSQL primary", e);
    }
    LOG.info("MSSQL primary initialized");
  }

  private void cacheCertFiles() {
    try {
      certFileBytes = copyFileFromContainer(CERT_CONTAINER_PATH, InputStream::readAllBytes);
      pvkFileBytes = copyFileFromContainer(PVK_CONTAINER_PATH, InputStream::readAllBytes);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to read cert files from primary container", e);
    }
  }

  private void transferCertsToReplica() {
    try {
      final Path certPath = Files.createTempFile("dbm_certificate", ".cer");
      final Path pvkPath = Files.createTempFile("dbm_certificate", ".pvk");
      Files.write(certPath, certFileBytes);
      Files.write(pvkPath, pvkFileBytes);

      replica.copyFileToContainer(MountableFile.forHostPath(certPath, 0644), CERT_CONTAINER_PATH);
      replica.copyFileToContainer(MountableFile.forHostPath(pvkPath, 0644), PVK_CONTAINER_PATH);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to transfer cert files to replica", e);
    }
  }

  private void initReplica() {
    LOG.info("Initializing MSSQL replica");
    try (final Connection conn =
            DriverManager.getConnection(replicaMasterJdbcUrl(), SA_USER, SA_PASSWORD);
        final Statement stmt = conn.createStatement()) {

      stmt.execute("CREATE MASTER KEY ENCRYPTION BY PASSWORD = '" + MASTER_KEY_PASSWORD + "'");
      stmt.execute(
          """
          CREATE CERTIFICATE dbm_certificate
            FROM FILE = '%s'
            WITH PRIVATE KEY (
              FILE = '%s',
              DECRYPTION BY PASSWORD = '%s'
            )
          """
              .formatted(CERT_CONTAINER_PATH, PVK_CONTAINER_PATH, CERT_KEY_PASSWORD));
      stmt.execute(
          """
          CREATE ENDPOINT [Hadr_endpoint]
            STATE = STARTED
            AS TCP (LISTENER_PORT = 5022)
            FOR DATABASE_MIRRORING (
              ROLE = ALL,
              AUTHENTICATION = CERTIFICATE dbm_certificate,
              ENCRYPTION = REQUIRED ALGORITHM AES
            )
          """);
      stmt.execute("ALTER AVAILABILITY GROUP [" + AG_NAME + "] JOIN WITH (CLUSTER_TYPE = NONE)");
      stmt.execute("ALTER AVAILABILITY GROUP [" + AG_NAME + "] GRANT CREATE ANY DATABASE");

    } catch (final Exception e) {
      throw new RuntimeException("Failed to initialize MSSQL replica", e);
    }
    LOG.info("MSSQL replica initialized");
  }

  private void waitForReplication() {
    LOG.info("Waiting for MSSQL replica to sync");
    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (final Connection conn =
                      DriverManager.getConnection(primaryMasterJdbcUrl(), SA_USER, SA_PASSWORD);
                  final Statement stmt = conn.createStatement();
                  final ResultSet rs =
                      stmt.executeQuery(
                          """
                          SELECT COUNT(*) FROM sys.dm_hadr_database_replica_states
                          WHERE is_local = 0
                            AND synchronization_health = 2
                          """)) {
                rs.next();
                final int count = rs.getInt(1);
                if (count < 1) {
                  throw new AssertionError("Expected 1 synced replica, but found " + count);
                }
              }
            });
    LOG.info("MSSQL replica is in sync with primary");
  }

  private String primaryMasterJdbcUrl() {
    return buildJdbcUrl(getHost(), getMappedPort(MSSQL_PORT), "master");
  }

  private String replicaMasterJdbcUrl() {
    return buildJdbcUrl(replica.getHost(), replica.getMappedPort(MSSQL_PORT), "master");
  }

  private static String buildJdbcUrl(final String host, final int port, final String database) {
    return "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=true;trustServerCertificate=true"
        .formatted(host, port, database);
  }
}
