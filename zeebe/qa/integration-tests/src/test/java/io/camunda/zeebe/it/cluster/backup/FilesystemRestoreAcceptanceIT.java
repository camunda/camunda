/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.camunda.configuration.Camunda;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class FilesystemRestoreAcceptanceIT implements RestoreAcceptance {
  private static @TempDir Path tempDir;

  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());

  @Override
  public void configureBackupStore(final BrokerCfg cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);

    final var config = new FilesystemBackupStoreConfig();
    config.setBasePath(basePath.toAbsolutePath().toString());
    backup.setFilesystem(config);
  }

  @Override
  public void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(io.camunda.configuration.Backup.BackupStoreType.FILESYSTEM);

    final var config = backup.getFilesystem();
    config.setBasePath(basePath.toAbsolutePath().toString());
    backup.setFilesystem(config);
  }
}
