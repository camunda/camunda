/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cross-version backup compatibility test for the filesystem backup store.
 *
 * <p>A host temporary directory is bind-mounted into the old broker container so that both the
 * container (writing the backup) and the in-process restore app (reading it) share the same storage
 * path.
 */
@Testcontainers
@ZeebeIntegration
final class FilesystemBackupCompatibilityIT implements BackupCompatibilityAcceptance {
  private static final String CONTAINER_BACKUP_PATH = "/usr/local/zeebe/backup";
  private static final Network NETWORK = Network.newNetwork();

  private static @TempDir Path backupDir;

  @BeforeAll
  static void makeBackupDirAccessible() throws IOException {
    // The container runs as user 'camunda' (uid 1001), but @TempDir is owned by the host user
    // with mode 700. We need to make it world-accessible so the container process can write to it.
    Files.setPosixFilePermissions(backupDir, PosixFilePermissions.fromString("rwxrwxrwx"));
  }

  @AfterAll
  static void makeBackupDirDeletable() throws IOException, InterruptedException {
    // Files created by the container are owned by uid 1001 and cannot be deleted by the host user.
    // Run a short-lived container as root to recursively fix permissions so @TempDir cleanup works.
    final var proc =
        new ProcessBuilder(
                "docker",
                "run",
                "--rm",
                "-v",
                backupDir.toAbsolutePath() + ":/data",
                "alpine:3",
                "chmod",
                "-R",
                "a+rwX",
                "/data")
            .inheritIO()
            .start();
    proc.waitFor();
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
    broker.withFileSystemBind(
        backupDir.toAbsolutePath().toString(), CONTAINER_BACKUP_PATH, BindMode.READ_WRITE);
  }
}
