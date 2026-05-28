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
 * (AG) with {@code CLUSTER_TYPE=NONE} — the Docker-friendly AG mode that needs no Windows Server
 * Failover Cluster.
 *
 * <p>Replication status can be monitored via {@code sys.dm_hadr_database_replica_states} on the
 * primary, which is what {@code ReplicationStatusMapper} queries with {@code databaseId="mssql"}.
 *
 * <h2>Why certificate-based authentication is required</h2>
 *
 * <p>SQL Server's database mirroring endpoint — the internal TCP channel used by AG replicas to
 * exchange log records — supports only three authentication modes: Windows (Kerberos/NTLM),
 * Negotiate, and Certificate. Windows and Negotiate both require Active Directory, which is
 * unavailable in plain Linux/Docker containers. Certificate-based authentication is therefore the
 * <em>only</em> supported option for AG on Linux.
 *
 * <p>The setup follows the procedure documented by Microsoft:
 *
 * <ul>
 *   <li><a
 *       href="https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-availability-group-configure-ha">
 *       Configure SQL Server Always On Availability Groups for high availability on Linux</a>
 *   <li><a
 *       href="https://learn.microsoft.com/en-us/sql/database-engine/database-mirroring/use-certificates-for-a-database-mirroring-endpoint-transact-sql">
 *       Use certificates for a database mirroring endpoint (T-SQL)</a>
 * </ul>
 *
 * <h2>Startup sequence</h2>
 *
 * <ol>
 *   <li>Start the primary container and wait for SQL Server to accept connections.
 *   <li>Initialise the primary: create the database, master key, mirroring certificate, mirroring
 *       endpoint, and the AG definition (which lists the replica as a future member).
 *   <li>Read the certificate and private-key files out of the primary container into memory.
 *   <li>Start the replica container and wait for SQL Server to accept connections.
 *   <li>Inject the certificate files into the replica container.
 *   <li>Initialise the replica: recreate the master key, import the certificate, create the
 *       mirroring endpoint, and join the AG.
 *   <li>Wait until the replica reports {@code synchronization_health = 2} (HEALTHY).
 * </ol>
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

  @Override
  public String getJdbcUrl() {
    return buildJdbcUrl(getHost(), getMappedPort(MSSQL_PORT), DATABASE_NAME);
  }

  @Override
  public String getUsername() {
    return SA_USER;
  }

  @Override
  public String getPassword() {
    return SA_PASSWORD;
  }

  @Override
  public void stopReplica() {
    if (!replicaStopped) {
      LOG.info("Stopping MSSQL replica");
      replicaStopped = true;
      replica.stop();
      LOG.info("MSSQL replica stopped");
    }
  }

  @Override
  public void startReplica() {
    LOG.info("Starting MSSQL replica");
    replicaStopped = false;
    replica.start();
    waitForSqlServerReady(replicaMasterJdbcUrl());
    transferCertsToReplica();
    initReplica();
    waitForReplication();
  }

  /**
   * Polls the given JDBC URL with {@code SELECT 1} until SQL Server accepts connections or the
   * 2-minute timeout is exceeded. Used for both the primary and the replica after container start.
   */
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

  /**
   * Initialises the primary node: creates the {@code camunda} database (with {@code RECOVERY FULL}
   * and an initial backup to establish the log chain), creates the master key and mirroring
   * certificate, backs up the certificate and its private key to {@code /tmp}, starts the mirroring
   * endpoint on TCP port 5022, creates the AG, and grants automatic seeding permission.
   */
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

  /**
   * Reads the certificate ({@code .cer}) and private-key ({@code .pvk}) files out of the running
   * primary container into JVM heap memory. The bytes are kept so the replica can receive them
   * again if it is restarted via {@link #startReplica()}, without having to re-initialise the
   * primary.
   */
  private void cacheCertFiles() {
    try {
      certFileBytes = copyFileFromContainer(CERT_CONTAINER_PATH, InputStream::readAllBytes);
      pvkFileBytes = copyFileFromContainer(PVK_CONTAINER_PATH, InputStream::readAllBytes);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to read cert files from primary container", e);
    }
  }

  /**
   * Writes the cached certificate bytes to temporary host files and copies them into the replica
   * container at the same {@code /tmp} paths that {@link #initReplica()} expects. Testcontainers'
   * {@link MountableFile} is used rather than a shared Docker volume because these containers share
   * no volumes — the host JVM is the transfer intermediary.
   */
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

  /**
   * Initialises a replica node: creates its own master key, imports the certificate that was
   * transferred from the primary, starts the mirroring endpoint on TCP port 5022, and joins the AG
   * that the primary already defined. The replica inherits the {@code camunda} database
   * automatically via {@code SEEDING_MODE=AUTOMATIC}.
   */
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

  /**
   * Polls {@code sys.dm_hadr_database_replica_states} on the primary until the replica row shows
   * {@code synchronization_health = 2} (HEALTHY), meaning log records are being applied and the
   * replica is caught up. Times out after 120 seconds.
   */
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
