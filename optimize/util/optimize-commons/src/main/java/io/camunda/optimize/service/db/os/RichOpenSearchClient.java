/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import io.camunda.optimize.service.db.os.externalcode.client.async.OpenSearchAsyncDocumentOperations;
import io.camunda.optimize.service.db.os.externalcode.client.async.OpenSearchAsyncSnapshotOperations;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchClusterOperations;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchIndexOperations;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchPipelineOperations;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchTaskOperations;
import io.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchTemplateOperations;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;

public class RichOpenSearchClient {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(RichOpenSearchClient.class);
  private final OptimizeIndexNameService indexNameService;
  // TODO slash unused operations with OPT-7352
  private final OpenSearchClusterOperations openSearchClusterOperations;
  private final OpenSearchDocumentOperations openSearchDocumentOperations;
  private final OpenSearchIndexOperations openSearchIndexOperations;
  private final OpenSearchPipelineOperations openSearchPipelineOperations;
  private final OpenSearchTaskOperations openSearchTaskOperations;
  private final OpenSearchTemplateOperations openSearchTemplateOperations;
  private final AsyncOperations asyncOperations;

  public RichOpenSearchClient(
      final OpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      final OptimizeIndexNameService indexNameService) {
    this.indexNameService = indexNameService;
    asyncOperations = new AsyncOperations(openSearchAsyncClient, indexNameService);
    openSearchClusterOperations =
        new OpenSearchClusterOperations(openSearchClient, indexNameService);
    openSearchDocumentOperations =
        new OpenSearchDocumentOperations(openSearchClient, indexNameService);
    openSearchIndexOperations = new OpenSearchIndexOperations(openSearchClient, indexNameService);
    openSearchPipelineOperations =
        new OpenSearchPipelineOperations(openSearchClient, indexNameService);
    openSearchTaskOperations = new OpenSearchTaskOperations(openSearchClient, indexNameService);
    openSearchTemplateOperations =
        new OpenSearchTemplateOperations(openSearchClient, indexNameService);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return indexNameService;
  }

  public AsyncOperations async() {
    return asyncOperations;
  }

  public OpenSearchClusterOperations cluster() {
    return openSearchClusterOperations;
  }

  public OpenSearchDocumentOperations doc() {
    return openSearchDocumentOperations;
  }

  public OpenSearchIndexOperations index() {
    return openSearchIndexOperations;
  }

  public OpenSearchPipelineOperations pipeline() {
    return openSearchPipelineOperations;
  }

  public OpenSearchTaskOperations task() {
    return openSearchTaskOperations;
  }

  public OpenSearchTemplateOperations template() {
    return openSearchTemplateOperations;
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
