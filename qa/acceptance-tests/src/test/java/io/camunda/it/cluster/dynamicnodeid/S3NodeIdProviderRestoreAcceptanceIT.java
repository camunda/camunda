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
import io.camunda.zeebe.dynamic.nodeid.repository.s3.S3NodeIdRepository;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers
@ZeebeIntegration
public final class S3NodeIdProviderRestoreAcceptanceIT {

  @Container
  private static final LocalStackContainer S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices("s3")
          .withEnv("LS_LOG", "trace");

  @AutoClose private static S3Client s3Client;
  private static final String NODE_ID_BUCKET_NAME = "node-id" + UUID.randomUUID().toString();
  private static final String BACKUP_BUCKET_NAME = "backup" + UUID.randomUUID().toString();
  private static final int CLUSTER_SIZE = 3;
  private static final int PARTITIONS_COUNT = 3;
  private static final int REPLICATION_FACTOR = 1;
  private static final Duration LEASE_DURATION = Duration.ofSeconds(20);

  private static @TempDir Path workingDirectory;

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

    s3Client.createBucket(b -> b.bucket(NODE_ID_BUCKET_NAME));
    s3Client.createBucket(b -> b.bucket(BACKUP_BUCKET_NAME));
  }

  @AfterEach
  void cleanup() {
    final var objects = s3Client.listObjects(b -> b.bucket(NODE_ID_BUCKET_NAME));
    objects.contents().parallelStream()
        .forEach(obj -> s3Client.deleteObject(b -> b.bucket(NODE_ID_BUCKET_NAME).key(obj.key())));
  }

  @Test
  void shouldBackupAndRestoreWithS3NodeIdProvider() throws IOException {
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
    testCluster.shutdown();

    // restore from backup
    restoreBackup(backupId);

    // restart cluster
    testCluster.start();
    testCluster.awaitCompleteTopology();

    // then - verify process instances can be completed
    assertThatJobsFromAllInstancesCanBeActivated(processInstancesCount);
  }

  private void assertThatJobsFromAllInstancesCanBeActivated(final int processInstancesCount) {
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

  private void restoreBackup(final long backupId) throws IOException {
    final var restoreTasks = new ArrayList<CompletableFuture<Void>>();

    FileUtil.deleteFolder(workingDirectory);

    for (int nodeId = 0; nodeId < CLUSTER_SIZE; nodeId++) {
      final var task = new CompletableFuture<Void>();
      // Restore must run in parallel as each restore will wait for other nodes to complete restore
      Thread.ofVirtual()
          .start(
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
                task.complete(null);
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
    s3.setBucketName(NODE_ID_BUCKET_NAME);
    s3.setLeaseDuration(LEASE_DURATION);
    s3.setEndpoint(S3.getEndpoint().toString());
    s3.setRegion(S3.getRegion());
    s3.setAccessKey(S3.getAccessKey());
    s3.setSecretKey(S3.getSecretKey());
  }

  public void configureBackupStore(final Camunda cfg) {
    // Configure backup store to use S3 to ensure no conflicts with node id provider which is also
    // using s3
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(PrimaryStorageBackup.BackupStoreType.S3);

    final var s3 = backup.getS3();
    s3.setRegion(S3.getRegion());
    s3.setSecretKey(S3.getSecretKey());
    s3.setBucketName(BACKUP_BUCKET_NAME);
    s3.setEndpoint(S3.getEndpoint().toString());
    s3.setAccessKey(S3.getAccessKey());
    s3.setForcePathStyleAccess(true);
  }
}
