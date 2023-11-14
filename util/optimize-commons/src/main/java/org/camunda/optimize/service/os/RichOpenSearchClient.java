/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.os.client.async.OpenSearchAsyncDocumentOperations;
import org.camunda.optimize.service.os.client.async.OpenSearchAsyncIndexOperations;
import org.camunda.optimize.service.os.client.async.OpenSearchAsyncTaskOperations;
import org.camunda.optimize.service.os.client.sync.OpenSearchClusterOperations;
import org.camunda.optimize.service.os.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.os.client.sync.OpenSearchIndexOperations;
import org.camunda.optimize.service.os.client.sync.OpenSearchPipelineOperations;
import org.camunda.optimize.service.os.client.sync.OpenSearchTaskOperations;
import org.camunda.optimize.service.os.client.sync.OpenSearchTemplateOperations;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;


@Conditional(OpenSearchCondition.class)
@Component
@Slf4j
public class RichOpenSearchClient {
  private final OptimizeIndexNameService indexNameService;

  // TODO slash unused operations with OPT-7352
  protected final OpenSearchClient openSearchClient;
  protected final OpenSearchClusterOperations openSearchClusterOperations;
  protected final OpenSearchDocumentOperations openSearchDocumentOperations;
  protected final OpenSearchIndexOperations openSearchIndexOperations;
  protected final OpenSearchPipelineOperations openSearchPipelineOperations;
  protected final OpenSearchTaskOperations openSearchTaskOperations;
  protected final OpenSearchTemplateOperations openSearchTemplateOperations;

  public RichOpenSearchClient(OpenSearchClient openSearchClient, OptimizeIndexNameService indexNameService) {
    this.openSearchClient = openSearchClient;
    this.indexNameService = indexNameService;
    openSearchClusterOperations = new OpenSearchClusterOperations(openSearchClient, indexNameService);
    openSearchDocumentOperations = new OpenSearchDocumentOperations(openSearchClient, indexNameService);
    openSearchIndexOperations = new OpenSearchIndexOperations(openSearchClient, indexNameService);
    openSearchPipelineOperations = new OpenSearchPipelineOperations(openSearchClient, indexNameService);
    openSearchTaskOperations = new OpenSearchTaskOperations(openSearchClient, indexNameService);
    openSearchTemplateOperations = new OpenSearchTemplateOperations(openSearchClient, indexNameService);
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

  public class Async {
    final OpenSearchAsyncDocumentOperations openSearchAsyncDocumentOperations;
    final OpenSearchAsyncIndexOperations openSearchAsyncIndexOperations;
    final OpenSearchAsyncTaskOperations openSearchAsyncTaskOperations;

    public Async(OpenSearchAsyncClient openSearchAsyncClient) {
      this.openSearchAsyncDocumentOperations = new OpenSearchAsyncDocumentOperations(openSearchAsyncClient, indexNameService);
      this.openSearchAsyncIndexOperations = new OpenSearchAsyncIndexOperations(openSearchAsyncClient, indexNameService);
      this.openSearchAsyncTaskOperations = new OpenSearchAsyncTaskOperations(openSearchAsyncClient, indexNameService);
    }

    public OpenSearchAsyncDocumentOperations doc() {
      return openSearchAsyncDocumentOperations;
    }

    public OpenSearchAsyncIndexOperations index() {
      return openSearchAsyncIndexOperations;
    }

    public OpenSearchAsyncTaskOperations task() {
      return openSearchAsyncTaskOperations;
    }
  }
}
