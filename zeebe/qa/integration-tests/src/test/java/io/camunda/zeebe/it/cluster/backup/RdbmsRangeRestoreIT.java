/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.util.FileUtil;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restores from a time range of backups via a separate, standalone restore application ({@code
 * TestRestoreApp}). See {@link RdbmsRangeRestoreTestBase} for the shared fixture and test cases,
 * and {@link InProcessRdbmsRangeRestoreIT} for the in-process counterpart.
 */
final class RdbmsRangeRestoreIT extends RdbmsRangeRestoreTestBase {

  private static @TempDir Path backupDir;

  private Path workingDirectory;

  @BeforeEach
  void setUp() {
    workingDirectory = broker.getWorkingDirectory();
  }

  @Override
  protected Path backupDir() {
    return backupDir;
  }

  @Override
  void restoreFromTimeRange(final Interval interval) throws Exception {
    broker.stop();
    FileUtil.deleteFolder(workingDirectory);
    FileUtil.ensureDirectoryExists(workingDirectory);
    try (final var restore = testRestoreApp(interval)) {
      restore.start();
    }
    broker.start();
  }

  @Override
  void restoreWithoutArguments() throws Exception {
    broker.stop();
    FileUtil.deleteFolder(workingDirectory);
    FileUtil.ensureDirectoryExists(workingDirectory);
    try (final var restore = testRestoreApp()) {
      restore.start();
    }
    broker.start();
  }

  @Override
  void assertRestoreFailsForMissingBackup(final Interval interval) throws Exception {
    broker.stop();
    FileUtil.deleteFolder(workingDirectory);
    FileUtil.ensureDirectoryExists(workingDirectory);
    try (final var restoreApp = testRestoreApp(interval)) {
      assertThatThrownBy(restoreApp::start)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No usable range found");
    }
  }

  private void configureRestoreApp(final Camunda cfg) {
    configureRdbms(cfg);
    // Filesystem backup store (same as broker)
    final var fsConfig = new Filesystem();
    fsConfig.setBasePath(backupDir().toAbsolutePath().toString());
    cfg.getData().getPrimaryStorage().getBackup().setFilesystem(fsConfig);
    cfg.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.FILESYSTEM);
    cfg.getData().getPrimaryStorage().getBackup().setContinuous(true);
  }

  @SuppressWarnings("resource")
  private TestRestoreApp testRestoreApp(final Interval interval) {
    return new TestRestoreApp()
        .withProperty("camunda.data.secondary-storage.type", "rdbms")
        .withUnifiedConfig(this::configureRestoreApp)
        .withWorkingDirectory(workingDirectory)
        .withTimeRange(interval.start(), interval.end());
  }

  @SuppressWarnings("resource")
  private TestRestoreApp testRestoreApp() {
    return new TestRestoreApp()
        .withProperty("camunda.data.secondary-storage.type", "rdbms")
        .withUnifiedConfig(this::configureRestoreApp)
        .withWorkingDirectory(workingDirectory);
  }
}
