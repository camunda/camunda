/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.os.externalcode.client.async.OpenSearchAsyncDocumentOperations;
import org.camunda.optimize.service.db.os.externalcode.client.async.OpenSearchAsyncSnapshotOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchClusterOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchIndexOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchPipelineOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchTaskOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchTemplateOperations;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;

@Slf4j
public class RichOpenSearchClient {
  public static class AsyncOperations {
    final OpenSearchAsyncDocumentOperations openSearchAsyncDocumentOperations;
    final OpenSearchAsyncSnapshotOperations openSearchAsyncSnapshotOperations;

    public AsyncOperations(
        OpenSearchAsyncClient openSearchAsyncClient, OptimizeIndexNameService indexNameService) {
      this.openSearchAsyncDocumentOperations =
          new OpenSearchAsyncDocumentOperations(indexNameService, openSearchAsyncClient);
      this.openSearchAsyncSnapshotOperations =
          new OpenSearchAsyncSnapshotOperations(indexNameService, openSearchAsyncClient);
    }

    public OpenSearchAsyncDocumentOperations doc() {
      return openSearchAsyncDocumentOperations;
    }

    public OpenSearchAsyncSnapshotOperations snapshot() {
      return openSearchAsyncSnapshotOperations;
    }
  }

  @Getter private final OptimizeIndexNameService indexNameService;

  // TODO slash unused operations with OPT-7352
  private final OpenSearchClusterOperations openSearchClusterOperations;
  private final OpenSearchDocumentOperations openSearchDocumentOperations;
  private final OpenSearchIndexOperations openSearchIndexOperations;
  private final OpenSearchPipelineOperations openSearchPipelineOperations;
  private final OpenSearchTaskOperations openSearchTaskOperations;
  private final OpenSearchTemplateOperations openSearchTemplateOperations;

  private final AsyncOperations asyncOperations;

  public RichOpenSearchClient(
      OpenSearchClient openSearchClient,
      OpenSearchAsyncClient openSearchAsyncClient,
      OptimizeIndexNameService indexNameService) {
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

  public String getIndexAliasFor(String indexName) {
    return indexNameService.getOptimizeIndexAliasForIndex(indexName);
  }
}
