/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.camunda.client.CamundaClient;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class FilesystemBackupAcceptanceIT implements BackupAcceptance {
  private static @TempDir Path tempDir;

  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(2)
          .withGatewaysCount(1)
          .withReplicationFactor(1)
          .withPartitionsCount(2)
          .withEmbeddedGateway(false)
          .withBrokerConfig(this::configureBroker)
          .build();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach(final @TempDir Path tempDir) throws IOException {
    client = cluster.newClientBuilder().build();
  }

  @Override
  public TestCluster getTestCluster() {
    return cluster;
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    broker.withUnifiedConfig(
        cfg -> {
          cfg.getData()
              .getPrimaryStorage()
              .getBackup()
              .setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);

          final var config = new Filesystem();
          config.setBasePath(basePath.toAbsolutePath().toString());
          cfg.getData().getPrimaryStorage().getBackup().setFilesystem(config);
        });
  }
}
