/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.backup;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebeHostData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cross-version backup compatibility test for the filesystem backup store.
 *
 * <p>A host temporary directory is bind-mounted into the old broker container so that both the
 * container (writing the backup) and the in-process restore app (reading it) share the same storage
 * path. The container is configured to run as the host user so that file ownership matches and no
 * special cleanup is needed.
 */
@Testcontainers
@ZeebeIntegration
final class FilesystemBackupCompatibilityIT implements BackupCompatibilityAcceptance {
  private static final String CONTAINER_BACKUP_PATH = "/usr/local/zeebe/backup";
  private static final Network NETWORK = Network.newNetwork();

  private final Path dataDir;
  private final Path backupDir;

  FilesystemBackupCompatibilityIT(@TempDir final Path backupDir, @TempDir final Path dataDir) {
    this.dataDir = dataDir;
    this.backupDir = backupDir;
  }

  @Override
  public Network getNetwork() {
    return NETWORK;
  }

  @Override
  public Map<String, String> backupStoreEnvVars() {
    return Map.of(
        "ZEEBE_BROKER_DATA_BACKUP_STORE",
        "FILESYSTEM",
        "ZEEBE_BROKER_DATA_BACKUP_FILESYSTEM_BASEPATH",
        CONTAINER_BACKUP_PATH);
  }

  @Override
  public void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);

    final var filesystem = backup.getFilesystem();
    filesystem.setBasePath(backupDir.toAbsolutePath().toString());
  }

  @Override
  public void customizeOldBroker(final ZeebeContainer broker) {
    // Run the container as the host user so that files written by the container are owned by the
    // same UID as the host process. This avoids bind-mount permission issues and removes the
    // need for any post-test permission cleanup.
    final int uid;
    final int gid;
    try {
      uid = (int) Files.getAttribute(backupDir, "unix:uid");
      gid = (int) Files.getAttribute(backupDir, "unix:gid");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    broker
        .withCreateContainerCmdModifier(cmd -> cmd.withUser(uid + ":" + gid))
        .withZeebeData(new ZeebeHostData(dataDir.toAbsolutePath().toString()))
        .withFileSystemBind(
            backupDir.toAbsolutePath().toString(), CONTAINER_BACKUP_PATH, BindMode.READ_WRITE);
  }
}
