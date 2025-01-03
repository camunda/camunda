/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.search.connect.configuration.DatabaseType;
import java.io.IOException;
import java.util.Collection;

public interface BackupDBClient extends AutoCloseable {
  void restore(String repositoryName, Collection<String> snapshots) throws IOException;

  void createRepository(String repositoryName) throws IOException;

  static BackupDBClient create(
      final TestStandaloneCamunda testStandaloneCamunda, final DatabaseType databaseType)
      throws IOException {
    return switch (databaseType) {
      case ELASTICSEARCH -> new ESDBClientBackup(testStandaloneCamunda);
      case OPENSEARCH -> new OSDBClientBackup(testStandaloneCamunda);
      default -> throw new IllegalStateException("Unsupported database type: " + databaseType);
    };
  }
}
