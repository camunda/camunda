/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import io.camunda.optimize.service.db.os.client.async.OpenSearchAsyncDocumentOperations;
import io.camunda.optimize.service.db.os.client.async.OpenSearchAsyncSnapshotOperations;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchIndexOperations;
import io.camunda.optimize.service.db.os.client.sync.OpenSearchTaskOperations;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;

public class RichOpenSearchClient {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(RichOpenSearchClient.class);

  private final OptimizeIndexNameService indexNameService;
  private final OpenSearchDocumentOperations openSearchDocumentOperations;
  private final OpenSearchIndexOperations openSearchIndexOperations;
  private final OpenSearchTaskOperations openSearchTaskOperations;
  private final AsyncOperations asyncOperations;

  public RichOpenSearchClient(
      final OpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final OptimizeIndexNameService indexNameService) {
    this.indexNameService = indexNameService;
    asyncOperations = new AsyncOperations(openSearchAsyncClient, indexNameService);
    openSearchDocumentOperations =
        new OpenSearchDocumentOperations(openSearchClient, indexNameService);
    openSearchIndexOperations = new OpenSearchIndexOperations(openSearchClient, indexNameService);
    openSearchTaskOperations = new OpenSearchTaskOperations(openSearchClient, indexNameService);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return indexNameService;
  }

  public AsyncOperations async() {
    return asyncOperations;
  }

  public OpenSearchDocumentOperations doc() {
    return openSearchDocumentOperations;
  }

  public OpenSearchIndexOperations index() {
    return openSearchIndexOperations;
  }

  public OpenSearchTaskOperations task() {
    return openSearchTaskOperations;
  }

  public String getIndexAliasFor(final String indexName) {
    return indexNameService.getOptimizeIndexAliasForIndex(indexName);
  }

  public static class AsyncOperations {

    final OpenSearchAsyncDocumentOperations openSearchAsyncDocumentOperations;
    final OpenSearchAsyncSnapshotOperations openSearchAsyncSnapshotOperations;

    public AsyncOperations(
        final OpenSearchAsyncClient openSearchAsyncClient,
        final OptimizeIndexNameService indexNameService) {
      openSearchAsyncDocumentOperations =
          new OpenSearchAsyncDocumentOperations(indexNameService, openSearchAsyncClient);
      openSearchAsyncSnapshotOperations =
          new OpenSearchAsyncSnapshotOperations(indexNameService, openSearchAsyncClient);
    }

    public OpenSearchAsyncDocumentOperations doc() {
      return openSearchAsyncDocumentOperations;
    }

    public OpenSearchAsyncSnapshotOperations snapshot() {
      return openSearchAsyncSnapshotOperations;
    }
  }
}
