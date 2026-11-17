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
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Restores with Elasticsearch as the secondary storage via a separate, standalone restore
 * application ({@code TestRestoreApp}). See {@link ElasticsearchRestoreTestBase} for the shared
 * fixture and test cases, and {@link InProcessElasticsearchRestoreIT} for the in-process
 * counterpart.
 */
@Testcontainers
final class ElasticsearchRestoreIT extends ElasticsearchRestoreTestBase {

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private static @TempDir Path backupDir;

  @Override
  protected Path backupDir() {
    return backupDir;
  }

  @Override
  protected String elasticsearchUrl() {
    return "http://" + ELASTICSEARCH.getHttpHostAddress();
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
      throws IOException {
    stopBrokerAndEmptyWorkingDirectory();
    try (final var restoreApp = testRestoreApp(request)) {
      // A rejection is thrown as is, or wrapped by the application runner
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
    configureStorages(cfg);
  }

  @SuppressWarnings("resource")
  private TestRestoreApp testRestoreApp(final RestoreRequest request) {
    final var restoreApp =
        new TestRestoreApp()
            .withProperty("camunda.data.secondary-storage.type", "elasticsearch")
            .withUnifiedConfig(this::configureRestoreApp)
            .withWorkingDirectory(broker.getWorkingDirectory())
            .withTimeRange(request.from(), request.to());
    if (!request.backupIds().isEmpty()) {
      restoreApp.withBackupId(request.backupIds().stream().mapToLong(Long::longValue).toArray());
    }
    return restoreApp;
  }
}
