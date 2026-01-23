/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster.dynamicnodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.NodeIdProvider.Type;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.it.util.TestHelper;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers
@ZeebeIntegration
public final class S3NodeIdProviderRestoreAcceptanceIT {

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices(Service.S3)
          .withEnv("LS_LOG", "trace");

  @AutoClose private static S3Client s3Client;
  private static final String BUCKET_NAME = UUID.randomUUID().toString();
  private static final int CLUSTER_SIZE = 3;
  private static final int PARTITIONS_COUNT = 3;
  private static final int REPLICATION_FACTOR = 1;
  private static final Duration LEASE_DURATION = Duration.ofSeconds(10);
  private static @TempDir Path tempDir;

  private static @TempDir Path workingDirectory;
  private final Path backupBasePath = tempDir.resolve(UUID.randomUUID().toString());

  @TestZeebe
  private final TestCluster testCluster =
      TestCluster.builder()
          .withName("s3-node-id-backup-restore-test")
          .withBrokersCount(CLUSTER_SIZE)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withoutNodeId()
          .withBrokerConfig(b -> b.withWorkingDirectory(workingDirectory))
          .withNodeConfig(
              app ->
                  app.withProperty(
                          "camunda.data.secondary-storage.type", SecondaryStorageType.none.name())
                      .withUnifiedConfig(
                          cfg -> {
                            configureNodeIdProvider(cfg);
                            configureBackupStore(cfg);
                          }))
          .build();

  @BeforeAll
  static void setupAll() {
    s3Client =
        S3NodeIdRepository.buildClient(
            new S3NodeIdRepository.S3ClientConfig(
                java.util.Optional.of(
                    new S3NodeIdRepository.S3ClientConfig.Credentials(
                        S3.getAccessKey(), S3.getSecretKey())),
                java.util.Optional.of(Region.of(S3.getRegion())),
                java.util.Optional.of(S3.getEndpoint())));

    s3Client.createBucket(b -> b.bucket(BUCKET_NAME));
  }

  @AfterEach
  void cleanup() {
    final var objects = s3Client.listObjects(b -> b.bucket(BUCKET_NAME));
    objects.contents().parallelStream()
        .forEach(obj -> s3Client.deleteObject(b -> b.bucket(BUCKET_NAME).key(obj.key())));
  }

  @Test
  void shouldBackupAndRestoreWithS3NodeIdProvider() {
    final var backupId = 42L;
    final var processInstancesCount = 3;

    // given - deploy process and create instances
    try (final var client = testCluster.newClientBuilder().build()) {
      TestHelper.deployResource(client, "process/service_tasks_v1.bpmn");

      for (int i = 0; i < processInstancesCount; i++) {
        TestHelper.startProcessInstance(client, "service_tasks_v1");
      }
    }

    // when - take backup
    takeBackup(backupId);

    // shutdown cluster
    testCluster.brokers().values().forEach(TestSpringApplication::stop);

    // restore from backup
    restoreBackup(backupId);

    // restart cluster
    testCluster.brokers().values().forEach(broker -> broker.start().await(TestHealthProbe.READY));
    testCluster.awaitCompleteTopology();

    // then - verify process instances can be completed
    try (final var client = testCluster.newClientBuilder().build()) {
      final var completedJobs = new ArrayList<ActivatedJob>();

      while (completedJobs.size() < processInstancesCount) {
        final var jobActivation =
            client
                .newActivateJobsCommand()
                .jobType("taskA")
                .maxJobsToActivate(processInstancesCount)
                .send()
                .toCompletableFuture();
        assertThat(jobActivation).succeedsWithin(Duration.ofSeconds(30));

        final var jobs = jobActivation.join().getJobs();
        assertThat(jobs).isNotEmpty();

        for (final var job : jobs) {
          TestHelper.completeJob(client, job.getKey());
          completedJobs.add(job);
        }
      }

      assertThat(completedJobs).hasSize(processInstancesCount);
    }
  }

  private void takeBackup(final long backupId) {
    final var broker = testCluster.brokers().values().iterator().next();
    final var actuator = BackupActuator.of(broker);
    final var partitions = PartitionsActuator.of(broker);

    partitions.takeSnapshot();

    Awaitility.await("Snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .until(() -> partitions.query().get(1).snapshotId(), Objects::nonNull);

    assertThatNoException().isThrownBy(() -> actuator.take(backupId));

    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var status = actuator.status(backupId);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(backupId, StateCode.COMPLETED);
            });
  }

  private void restoreBackup(final long backupId) {
    final var restoreTasks = new ArrayList<CompletableFuture<Void>>();

    for (int nodeId = 0; nodeId < CLUSTER_SIZE; nodeId++) {
      final var task =
          CompletableFuture.runAsync(
              () -> {
                final var restore =
                    new TestRestoreApp()
                        .withWorkingDirectory(workingDirectory)
                        .withUnifiedConfig(
                            cfg -> {
                              cfg.getCluster().setSize(CLUSTER_SIZE);
                              cfg.getCluster().setPartitionCount(PARTITIONS_COUNT);
                              cfg.getCluster().setReplicationFactor(REPLICATION_FACTOR);
                              configureNodeIdProvider(cfg);
                              configureBackupStore(cfg);
                            })
                        .withBackupId(backupId)
                        .start();
                restore.close();
              });
      restoreTasks.add(task);
    }

    // Wait for all restore operations to complete
    CompletableFuture.allOf(restoreTasks.toArray(new CompletableFuture[0])).join();
  }

  private void configureNodeIdProvider(final Camunda cfg) {
    cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
    cfg.getCluster().getNodeIdProvider().setType(Type.S3);

    final var s3 = cfg.getCluster().getNodeIdProvider().s3();
    s3.setTaskId(UUID.randomUUID().toString());
    s3.setBucketName(BUCKET_NAME);
    s3.setLeaseDuration(LEASE_DURATION);
    s3.setEndpoint(S3.getEndpoint().toString());
    s3.setRegion(S3.getRegion());
    s3.setAccessKey(S3.getAccessKey());
    s3.setSecretKey(S3.getSecretKey());
  }

  private void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);

    final var config = backup.getFilesystem();
    config.setBasePath(backupBasePath.toAbsolutePath().toString());
    backup.setFilesystem(config);
  }

  @SuppressWarnings("unused")
  private void configureBackupStore(final BrokerCfg cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);

    final var config = new FilesystemBackupStoreConfig();
    config.setBasePath(backupBasePath.toAbsolutePath().toString());
    backup.setFilesystem(config);
  }
}
