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
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restores without a secondary storage via a separate, standalone restore application ({@code
 * TestRestoreApp}). See {@link NoSecondaryStorageRestoreTestBase} for the shared fixture and test
 * cases, and {@link InProcessNoSecondaryStorageRestoreIT} for the in-process counterpart.
 */
final class NoSecondaryStorageRestoreIT extends NoSecondaryStorageRestoreTestBase {

  private static @TempDir Path backupDir;

  @Override
  protected Path backupDir() {
    return backupDir;
  }

  @Override
  void restore(final RestoreRequest request) throws Exception {
    stopBrokerAndEmptyWorkingDirectory();
    try (final var restoreApp = testRestoreApp(request)) {
      restoreApp.start();
    }
    // the broker restarts configured like the restore, so only the restored topology can bring
    // the partitions back to the backup's
    broker.unifiedConfig().getCluster().setPartitionCount(configuredPartitionCount);
    broker.start();
  }

  @Override
  void assertRestoreRejected(
      final RestoreRequest request,
      final Class<? extends RuntimeException> expectedType,
      final String message)
      throws Exception {
    assertRestoreFails(request, expectedType, message);
  }

  @Override
  void assertRestoreNotApplied(final RestoreRequest request, final String message)
      throws Exception {
    assertRestoreFails(request, IllegalStateException.class, message);
  }

  private void assertRestoreFails(
      final RestoreRequest request,
      final Class<? extends RuntimeException> expectedType,
      final String message)
      throws IOException {
    stopBrokerAndEmptyWorkingDirectory();
    try (final var restoreApp = testRestoreApp(request)) {
      // A rejection is thrown as is, a failure while restoring wrapped by the application runner
      var failure = catchThrowable(restoreApp::start);
      while (failure != null && failure.getCause() != null) {
        failure = failure.getCause();
      }
      assertThat(failure).isInstanceOf(expectedType).hasMessageContaining(message);
    }
  }

  /** The restore app requires an empty data directory to restore into. */
  private void stopBrokerAndEmptyWorkingDirectory() throws IOException {
    broker.stop();
    FileUtil.deleteFolder(broker.getWorkingDirectory());
    FileUtil.ensureDirectoryExists(broker.getWorkingDirectory());
  }

  private void configureRestoreApp(final Camunda cfg) {
    cfg.getCluster().setPartitionCount(configuredPartitionCount);
    configureBackupStore(cfg);
  }

  @SuppressWarnings("resource")
  private TestRestoreApp testRestoreApp(final RestoreRequest request) {
    final var restoreApp =
        new TestRestoreApp()
            .withProperty("camunda.data.secondary-storage.type", "none")
            .withUnifiedConfig(this::configureRestoreApp)
            .withWorkingDirectory(broker.getWorkingDirectory())
            .withTimeRange(request.from(), request.to());
    if (!request.backupIds().isEmpty()) {
      restoreApp.withBackupId(request.backupIds().stream().mapToLong(Long::longValue).toArray());
    }
    return restoreApp;
  }
}
