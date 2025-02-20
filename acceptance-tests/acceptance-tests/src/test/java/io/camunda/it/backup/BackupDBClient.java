/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import io.camunda.search.connect.configuration.DatabaseType;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * interface to abstract Elasticsearch/Opensearch clients for testing purposes. Methods defined here
 * are not implemented elsewhere as they are typically performed by users, not by the camunda
 * application.
 */
public interface BackupDBClient extends AutoCloseable {
  void restore(String repositoryName, Collection<String> snapshots) throws IOException;

  void createRepository(String repositoryName) throws IOException;

  static BackupDBClient create(final String url, final DatabaseType databaseType)
      throws IOException {
    return switch (databaseType) {
      case ELASTICSEARCH -> new ESDBClientBackup(url);
      case OPENSEARCH -> new OSDBClientBackup(url);
      default -> throw new IllegalStateException("Unsupported database type: " + databaseType);
    };
  }

  // TODO remove this when purge functionality is available
  void deleteAllIndices(final String indexPrefix) throws IOException;

  List<String> cat() throws IOException;
}
