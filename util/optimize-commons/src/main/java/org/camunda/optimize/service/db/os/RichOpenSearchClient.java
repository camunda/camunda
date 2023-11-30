/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchClusterOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchIndexOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchPipelineOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchTaskOperations;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchTemplateOperations;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;


@Conditional(OpenSearchCondition.class)
@Component
@Slf4j
public class RichOpenSearchClient {

  @Getter
  private final OptimizeIndexNameService indexNameService;

  // TODO slash unused operations with OPT-7352
  private final OpenSearchClusterOperations openSearchClusterOperations;
  private final OpenSearchDocumentOperations openSearchDocumentOperations;
  private final OpenSearchIndexOperations openSearchIndexOperations;
  private final OpenSearchPipelineOperations openSearchPipelineOperations;
  private final OpenSearchTaskOperations openSearchTaskOperations;
  private final OpenSearchTemplateOperations openSearchTemplateOperations;

  public RichOpenSearchClient(OpenSearchClient openSearchClient, OptimizeIndexNameService indexNameService) {
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

}
