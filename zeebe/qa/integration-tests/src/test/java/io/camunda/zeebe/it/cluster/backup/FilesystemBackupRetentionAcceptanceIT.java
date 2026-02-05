/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupConfig;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.io.TempDir;

@ZeebeIntegration
public class FilesystemBackupRetentionAcceptanceIT implements BackupRetentionAcceptance {
  private static @TempDir Path tempDir;
  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());
  private BackupStore backupStore;

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKER_COUNT)
          .withGatewaysCount(1)
          .withReplicationFactor(REPLICATION_FACTOR)
          .withPartitionsCount(PARTITION_COUNT)
          .withEmbeddedGateway(false)
          .withNodeConfig(
              node ->
                  node.withProperty("zeebe.clock.controlled", true)
                      .withProperty("management.endpoints.web.exposure.include", "*"))
          .build();

  @Override
  public TestCluster getTestCluster() {
    return cluster;
  }

  @Override
  public BackupStore getBackupStore() {
    return backupStore;
  }

  @Override
  public void assertManifestDoesNotExist(final long backupId) {
    final Path manifestsDir = basePath.resolve("manifests");
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId -> {
              final Path partitionDir = manifestsDir.resolve(String.valueOf(partitionId));
              final Path checkpointDir = partitionDir.resolve(String.valueOf(backupId));
              assertThat(Files.exists(checkpointDir)).isFalse();
            });
  }

  @Override
  public void assertContentsDoNotExist(final long backupId) {
    final Path contentsDir = basePath.resolve("contents");
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId -> {
              final Path partitionDir = contentsDir.resolve(String.valueOf(partitionId));
              final Path checkpointDir = partitionDir.resolve(String.valueOf(backupId));
              assertThat(Files.exists(checkpointDir)).isFalse();
            });
  }

  @Override
  public Consumer<Camunda> backupConfig() {
    return cfg -> {
      cfg.getData()
          .getPrimaryStorage()
          .getBackup()
          .setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);

      final var config = new Filesystem();
      config.setBasePath(basePath.toAbsolutePath().toString());
      cfg.getData().getPrimaryStorage().getBackup().setFilesystem(config);

      backupStore =
          FilesystemBackupStore.of(
              new FilesystemBackupConfig(basePath.toAbsolutePath().toString()));
    };
  }
}
