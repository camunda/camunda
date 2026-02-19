/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.document;

import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * interface to abstract Elasticsearch/Opensearch clients for testing purposes. Methods defined here
 * are not implemented elsewhere as they are typically performed by users, not by the camunda
 * application.
 */
public interface DocumentClient extends AutoCloseable {
  void restore(String repositoryName, Collection<String> snapshots) throws IOException;

  void createRepository(String repositoryName) throws IOException;

  static DocumentClient create(
      final String url, final DatabaseType databaseType, final Executor executor)
      throws IOException {
    return switch (databaseType) {
      case ELASTICSEARCH -> new ESClient(url, executor);
      case OPENSEARCH -> new OSClient(url);
      default -> throw new IllegalStateException("Unsupported database type: " + databaseType);
    };
  }

  // TODO remove this when purge functionality is available
  void deleteAllIndices(final String indexPrefix) throws IOException;

  BackupRepository zeebeBackupRepository(
      String repositoryName, SnapshotNameProvider snapshotNameProvider);

  List<String> cat(String indexPrefix) throws IOException;

  /**
   * Index a document with the given ID and content to the specified index.
   *
   * @param indexName the name of the index
   * @param documentId the ID of the document
   * @param document the document content to index
   * @throws IOException if indexing fails
   */
  void index(String indexName, String documentId, Object document) throws IOException;

  /**
   * Index a document with retry logic for better reliability.
   *
   * @param indexName the name of the index
   * @param documentId the ID of the document
   * @param document the document content to index
   * @throws IOException if indexing fails after retries
   */
  default void indexWithRetry(
      final String indexName, final String documentId, final Object document) throws IOException {
    final int maxRetries = 3;
    IOException lastException = null;

    for (int attempt = 0; attempt < maxRetries; attempt++) {
      try {
        index(indexName, documentId, document);
        return; // Success, exit retry loop
      } catch (final IOException e) {
        lastException = e;
        if (attempt < maxRetries - 1) {
          try {
            Thread.sleep(100 * (attempt + 1)); // Progressive backoff
          } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during retry", ie);
          }
        }
      }
    }

    throw new IOException(
        "Failed to index document after " + maxRetries + " attempts", lastException);
  }

  /**
   * Refresh the specified index to make recently indexed documents available for search.
   *
   * @param indexName the name of the index to refresh
   * @throws IOException if refresh fails
   */
  void refresh(String indexName) throws IOException;

  /**
   * Bulk index multiple documents for better performance.
   *
   * @param indexName the name of the index
   * @param documents a collection of documents with their IDs
   * @throws IOException if bulk indexing fails
   */
  void bulkIndex(String indexName, Collection<DocumentWithId> documents) throws IOException;

  /**
   * Perform a search on the given index with the specified size and return the hits as a list of
   * maps.
   *
   * @param indexName the name of the index
   * @param size the maximum number of results
   * @return list of document maps
   * @throws IOException if the search fails
   */
  List<Map<String, Object>> search(String indexName, int size) throws IOException;

  /** Helper record to represent a document with its ID for bulk operations. */
  record DocumentWithId(String id, Object document) {}
}
