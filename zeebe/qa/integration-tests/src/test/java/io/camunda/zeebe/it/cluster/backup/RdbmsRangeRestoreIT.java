/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.data.primarystorage.backup.Filesystem;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
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
  void restore(final RestoreTarget target) throws Exception {
    stopBrokerAndEmptyWorkingDirectory();
    try (final var restoreApp = testRestoreApp(target)) {
      restoreApp.start();
    }
    // the broker restarts configured like the restore, so only the restored topology can bring
    // the partitions back to the backup's
    broker.unifiedConfig().getCluster().setPartitionCount(configuredPartitionCount);
    broker.start();
  }

  @Override
  void assertRestoreRejected(
      final RestoreTarget target,
      final Class<? extends RuntimeException> expectedType,
      final String message)
      throws Exception {
    stopBrokerAndEmptyWorkingDirectory();
    try (final var restoreApp = testRestoreApp(target)) {
      // A rejection is thrown as is, or wrapped by the application runner
      var failure = catchThrowable(restoreApp::start);
      while (failure != null && failure.getCause() != null) {
        failure = failure.getCause();
      }
      assertThat(failure).isInstanceOf(expectedType).hasMessageContaining(message);
    }
  }

  @Override
  void assertRestoreNotApplied(final RestoreTarget target, final String expectedMessage)
      throws Exception {
    stopBrokerAndEmptyWorkingDirectory();
    try (final var restoreApp = testRestoreApp(target)) {
      // A failure while restoring is wrapped by the application runner
      var failure = catchThrowable(restoreApp::start);
      while (failure != null && failure.getCause() != null) {
        failure = failure.getCause();
      }
      assertThat(failure)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(expectedMessage);
    }
  }

  /** The restore app requires an empty data directory to restore into. */
  private void stopBrokerAndEmptyWorkingDirectory() throws IOException {
    broker.stop();
    FileUtil.deleteFolder(workingDirectory);
    FileUtil.ensureDirectoryExists(workingDirectory);
  }

  private void configureRestoreApp(final Camunda cfg) {
    cfg.getCluster().setPartitionCount(configuredPartitionCount);
    configureRdbms(cfg);
    // Filesystem backup store (same as broker)
    final var fsConfig = new Filesystem();
    fsConfig.setBasePath(backupDir().toAbsolutePath().toString());
    cfg.getData().getPrimaryStorage().getBackup().setFilesystem(fsConfig);
    cfg.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.FILESYSTEM);
    cfg.getData().getPrimaryStorage().getBackup().setContinuous(true);
  }

  @SuppressWarnings("resource")
  private TestRestoreApp testRestoreApp(final RestoreTarget target) {
    final var restoreApp =
        new TestRestoreApp()
            .withProperty("camunda.data.secondary-storage.type", "rdbms")
            .withUnifiedConfig(this::configureRestoreApp)
            .withWorkingDirectory(workingDirectory)
            .withTimeRange(target.from(), target.to());
    if (!target.backupIds().isEmpty()) {
      restoreApp.withBackupId(target.backupIds().stream().mapToLong(Long::longValue).toArray());
    }
    return restoreApp;
  }
}
