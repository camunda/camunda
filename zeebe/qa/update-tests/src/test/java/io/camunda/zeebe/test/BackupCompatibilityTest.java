/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Azure;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.S3;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.azure.AzureBackupConfig;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import io.camunda.zeebe.util.VersionUtil;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeTopologyWaitStrategy;
import io.zeebe.containers.ZeebeVolume;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Tests that backups taken by the previous version can be found and restored by the current
 * version. This ensures backward compatibility of the backup store structure.
 *
 * <p>The test starts a container with the previous Camunda version, creates workload, takes a
 * backup, stops the container, and then uses the current version (as a {@code @ZeebeIntegration}
 * test) to restore and verify the backup.
 */
@Testcontainers
@ZeebeIntegration
class BackupCompatibilityTest {

  private static final String JOB_TYPE = "test-job";
  private static final String PROCESS_ID = "test-process";
  private static final BpmnModelInstance SIMPLE_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .endEvent("end")
          .done();

  private static final int PARTITION_COUNT = 2;
  private static final long BACKUP_ID = 100L;
  private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(30);

  // Testcontainers for external backup stores
  @Container private static final MinioContainer S3 = new MinioContainer();
  @Container private static final GcsContainer GCS = new GcsContainer();
  @Container private static final AzuriteContainer AZURITE = new AzuriteContainer();

  private Network network;
  private ZeebeVolume volume;
  private ZeebeContainer oldBroker;
  private CamundaClient oldClient;
  private BackupActuator oldActuator;

  // Current version cluster managed by @ZeebeIntegration
  @TestZeebe(autoStart = false)
  private TestCluster currentCluster;

  @TempDir private Path tempDir;

  private String bucketName;
  private String containerName;

  @BeforeEach
  void setUp() {
    network = Network.newNetwork();
    volume = ZeebeVolume.newVolume();
    // Generate unique names for each test
    bucketName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    containerName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  }

  @AfterEach
  void tearDown() {
    if (oldClient != null) {
      oldClient.close();
      oldClient = null;
    }

    if (oldBroker != null) {
      oldBroker.shutdownGracefully(CLOSE_TIMEOUT);
      oldBroker = null;
    }

    if (network != null) {
      network.close();
      network = null;
    }

    if (volume != null) {
      volume.close();
      volume = null;
    }
  }

  @ParameterizedTest(name = "Backup compatibility test with {0} store")
  @EnumSource(
      value = BackupStoreType.class,
      names = {"S3", "GCS", "AZURE", "FILESYSTEM"})
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void shouldRestoreBackupFromPreviousVersion(final BackupStoreType storeType) throws Exception {
    // given - old version container with backup store
    startOldVersionContainer(storeType);
    createBucketIfNeeded(storeType);

    // when - create workload on old version
    final Set<Long> jobKeys = createWorkloadOnOldVersion();

    // and - take backup on old version
    takeBackupOnOldVersion();

    // and - stop old version
    stopOldVersionContainer();

    // and - restore backup to current version data directory
    final Path currentDataDir = tempDir.resolve("current-data");
    restoreBackupToDirectory(storeType, currentDataDir);

    // then - start current version and verify workload can resume
    startCurrentVersionCluster(storeType, currentDataDir);
    verifyBackupCanBeFound();
    verifyWorkloadCanResume(jobKeys);
  }

  private void startOldVersionContainer(final BackupStoreType storeType) {
    final DockerImageName oldImage =
        DockerImageName.parse("camunda/zeebe").withTag(VersionUtil.getPreviousVersion());

    oldBroker =
        new ZeebeContainer(oldImage)
            .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
            .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", String.valueOf(PARTITION_COUNT))
            .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m")
            // Disable secondary storage and database
            .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "NONE")
            .withEnv("CAMUNDA_DATABASE_TYPE", "NONE")
            .withEnv("CAMUNDA_REST_ENABLED", "false")
            // Security settings
            .withEnv("CAMUNDA_SEARCH_ENGINE_SCHEMA_MANAGER_CREATE", "false")
            .withEnv("CAMUNDA_API_UNPROTECTED", "true")
            .withEnv("CAMUNDA_SECURITY_AUTHORIZATION_ENABLED", "false")
            .withTopologyCheck(new ZeebeTopologyWaitStrategy(1, 1, PARTITION_COUNT))
            .withZeebeData(volume)
            .withNetwork(network);

    // Configure backup store
    configureOldBrokerBackupStore(storeType);

    // User configuration for compatibility
    oldBroker.withCreateContainerCmdModifier(
        createContainerCmd -> createContainerCmd.withUser("1001:0"));

    oldBroker.start();

    oldClient =
        CamundaClient.newClientBuilder()
            .grpcAddress(oldBroker.getGrpcAddress())
            .preferRestOverGrpc(false)
            .build();

    oldActuator = BackupActuator.of(oldBroker);
  }

  private void configureOldBrokerBackupStore(final BackupStoreType storeType) {
    switch (storeType) {
      case S3 -> {
        oldBroker
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_STORE", "S3")
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_S3_BUCKETNAME", bucketName)
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_S3_ENDPOINT", S3.externalEndpoint())
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_S3_REGION", S3.region())
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_S3_ACCESSKEY", S3.accessKey())
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_S3_SECRETKEY", S3.secretKey())
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_S3_FORCEPATHSTYLEACCESS", "true");
      }
      case GCS -> {
        oldBroker
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_STORE", "GCS")
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_GCS_BUCKETNAME", bucketName)
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_GCS_HOST", GCS.externalEndpoint())
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_GCS_AUTH", "NONE");
      }
      case AZURE -> {
        oldBroker
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_STORE", "AZURE")
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_AZURE_CONTAINERNAME", containerName)
            .withEnv(
                "CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_AZURE_CONNECTIONSTRING",
                AZURITE.getConnectString());
      }
      case FILESYSTEM -> {
        final String backupPath = "/tmp/backup-store";
        oldBroker
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_STORE", "FILESYSTEM")
            .withEnv("CAMUNDA_DATA_PRIMARYSTORAGE_BACKUP_FILESYSTEM_BASEPATH", backupPath)
            .withFileSystemBind(tempDir.resolve("backup-store").toString(), backupPath);
      }
      default -> throw new IllegalArgumentException("Unsupported backup store type: " + storeType);
    }
  }

  private void createBucketIfNeeded(final BackupStoreType storeType) throws Exception {
    switch (storeType) {
      case S3 -> {
        final var config =
            new S3BackupConfig.Builder()
                .withBucketName(bucketName)
                .withEndpoint(S3.externalEndpoint())
                .withRegion(S3.region())
                .withCredentials(S3.accessKey(), S3.secretKey())
                .forcePathStyleAccess(true)
                .build();
        try (final var client = S3BackupStore.buildClient(config)) {
          client.createBucket(builder -> builder.bucket(bucketName).build()).join();
        }
      }
      case GCS -> {
        final var config =
            new GcsBackupConfig.Builder()
                .withoutAuthentication()
                .withHost(GCS.externalEndpoint())
                .withBucketName(bucketName)
                .build();
        try (final var client = GcsBackupStore.buildClient(config)) {
          client.create(com.google.cloud.storage.BucketInfo.of(bucketName));
        }
      }
      case AZURE -> {
        final var config =
            new AzureBackupConfig.Builder()
                .withConnectionString(AZURITE.getConnectString())
                .withContainerName(containerName)
                .build();
        final var client = AzureBackupStore.buildClient(config);
        final var containerClient = client.getBlobContainerClient(containerName);
        containerClient.createIfNotExists();
      }
      case FILESYSTEM -> {
        // Create directory if needed
        Files.createDirectories(tempDir.resolve("backup-store"));
      }
      default -> throw new IllegalArgumentException("Unsupported backup store type: " + storeType);
    }
  }

  private Set<Long> createWorkloadOnOldVersion() {
    // Deploy process
    oldClient.newDeployCommand().addProcessModel(SIMPLE_PROCESS, "process.bpmn").send().join();

    // Create process instances with jobs on all partitions
    final Set<Long> jobKeys = new HashSet<>();
    final Set<Integer> partitionsWithJobs = new HashSet<>();

    // Create jobs until we have at least one on each partition
    while (partitionsWithJobs.size() < PARTITION_COUNT) {
      final long instanceKey =
          oldClient
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();

      // Poll for the job to get its key and partition
      final var job =
          oldClient
              .newActivateJobsCommand()
              .jobType(JOB_TYPE)
              .maxJobsToActivate(1)
              .timeout(Duration.ofMinutes(5))
              .send()
              .join();

      if (!job.getJobs().isEmpty()) {
        final ActivatedJob activatedJob = job.getJobs().get(0);
        jobKeys.add(activatedJob.getKey());
        partitionsWithJobs.add(Protocol.decodePartitionId(activatedJob.getKey()));
      }

      // If we haven't covered all partitions, create more instances
      if (partitionsWithJobs.size() < PARTITION_COUNT) {
        // Add a small delay to avoid tight loop
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
    }

    return jobKeys;
  }

  private void takeBackupOnOldVersion() {
    oldActuator.take(BACKUP_ID);

    // Wait for backup to complete
    Awaitility.await("Backup must be completed on old version")
        .timeout(Duration.ofMinutes(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final BackupInfo status = oldActuator.status(BACKUP_ID);
              assertThat(status.getBackupId()).isEqualTo(BACKUP_ID);
              assertThat(status.getState()).isEqualTo(StateCode.COMPLETED);
              assertThat(status.getDetails()).hasSize(PARTITION_COUNT);
            });
  }

  private void stopOldVersionContainer() {
    if (oldClient != null) {
      oldClient.close();
      oldClient = null;
    }

    if (oldBroker != null) {
      oldBroker.shutdownGracefully(CLOSE_TIMEOUT);
      oldBroker = null;
    }
  }

  private void restoreBackupToDirectory(final BackupStoreType storeType, final Path dataDir)
      throws IOException {
    Files.createDirectories(dataDir);

    final var restoreConfig = new Camunda();
    restoreConfig.getCluster().setNodeId(0);
    restoreConfig.getCluster().setPartitionCount(PARTITION_COUNT);
    restoreConfig.getCluster().setSize(1);
    restoreConfig.getData().getPrimaryStorage().setDirectory(dataDir.toAbsolutePath().toString());

    // Configure backup store for restore
    configureRestoreBackupStore(restoreConfig, storeType);

    try (final var restoreApp = new TestRestoreApp(restoreConfig).withBackupId(BACKUP_ID)) {
      restoreApp.start();
    }
  }

  private void configureRestoreBackupStore(final Camunda config, final BackupStoreType storeType) {
    final var backup = config.getData().getPrimaryStorage().getBackup();

    switch (storeType) {
      case S3 -> {
        backup.setStore(BackupStoreType.S3);
        final var s3Config = new S3();
        s3Config.setBucketName(bucketName);
        s3Config.setEndpoint(S3.externalEndpoint());
        s3Config.setRegion(S3.region());
        s3Config.setAccessKey(S3.accessKey());
        s3Config.setSecretKey(S3.secretKey());
        s3Config.setForcePathStyleAccess(true);
        backup.setS3(s3Config);
      }
      case GCS -> {
        backup.setStore(BackupStoreType.GCS);
        final var gcsConfig = new Gcs();
        gcsConfig.setBucketName(bucketName);
        gcsConfig.setHost(GCS.externalEndpoint());
        gcsConfig.setAuth(Gcs.GcsBackupStoreAuth.NONE);
        backup.setGcs(gcsConfig);
      }
      case AZURE -> {
        backup.setStore(BackupStoreType.AZURE);
        final var azureConfig = new Azure();
        azureConfig.setBasePath(containerName);
        azureConfig.setConnectionString(AZURITE.getConnectString());
        backup.setAzure(azureConfig);
      }
      case FILESYSTEM -> {
        backup.setStore(BackupStoreType.FILESYSTEM);
        final var fsConfig = new Filesystem();
        fsConfig.setBasePath(tempDir.resolve("backup-store").toAbsolutePath().toString());
        backup.setFilesystem(fsConfig);
      }
      default -> throw new IllegalArgumentException("Unsupported backup store type: " + storeType);
    }
  }

  private void startCurrentVersionCluster(final BackupStoreType storeType, final Path dataDir) {
    currentCluster =
        TestCluster.builder()
            .withBrokersCount(1)
            .withPartitionsCount(PARTITION_COUNT)
            .withReplicationFactor(1)
            .withBrokerConfig(
                broker -> {
                  // Use the restored data directory
                  broker.withUnifiedConfig(
                      config -> {
                        config
                            .getData()
                            .getPrimaryStorage()
                            .setDirectory(dataDir.toAbsolutePath().toString());
                        configureRestoreBackupStore(config, storeType);
                      });
                })
            .build();

    currentCluster.start().awaitCompleteTopology();
  }

  private void verifyBackupCanBeFound() {
    final var actuator = BackupActuator.of(currentCluster.anyGateway());

    // Verify backup exists and is in COMPLETED state
    Awaitility.await("Backup should be found by current version")
        .timeout(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final BackupInfo status = actuator.status(BACKUP_ID);
              assertThat(status.getBackupId()).isEqualTo(BACKUP_ID);
              assertThat(status.getState()).isEqualTo(StateCode.COMPLETED);
            });
  }

  private void verifyWorkloadCanResume(final Set<Long> expectedJobKeys) {
    // Verify that jobs created before backup can be activated and completed
    final Set<Long> activatedJobKeys = new HashSet<>();

    try (final var client = currentCluster.newClientBuilder().build()) {
      // Activate and complete jobs
      Awaitility.await("All jobs should be activated")
          .timeout(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(500))
          .untilAsserted(
              () -> {
                final var jobs =
                    client
                        .newActivateJobsCommand()
                        .jobType(JOB_TYPE)
                        .maxJobsToActivate(10)
                        .timeout(Duration.ofMinutes(5))
                        .send()
                        .join();

                for (final ActivatedJob job : jobs.getJobs()) {
                  activatedJobKeys.add(job.getKey());
                  // Complete the job
                  client.newCompleteCommand(job.getKey()).send().join();
                }

                assertThat(activatedJobKeys).containsExactlyInAnyOrderElementsOf(expectedJobKeys);
              });
    }
  }
}
