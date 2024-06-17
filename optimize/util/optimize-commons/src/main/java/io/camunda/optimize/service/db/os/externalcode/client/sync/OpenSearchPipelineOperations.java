/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.externalcode.client.sync;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;

public class OpenSearchPipelineOperations extends OpenSearchRetryOperation {
  public OpenSearchPipelineOperations(
      OpenSearchClient openSearchClient, final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  public boolean addPipelineWithRetries(String name, String definition) {
    return executeWithRetries(
        "AddPipeline " + name,
        () ->
            openSearchClient
                .ingest()
                .putPipeline(i -> i.id(name).meta(name, JsonData.of(definition)))
                .acknowledged());
  }

  public boolean removePipelineWithRetries(String name) {
    return executeWithRetries(
        "RemovePipeline " + name,
        () -> openSearchClient.ingest().deletePipeline(dp -> dp.id(name)).acknowledged());
  }
}
