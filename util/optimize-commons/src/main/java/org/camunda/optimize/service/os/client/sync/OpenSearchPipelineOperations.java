/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.client.sync;

import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;

public class OpenSearchPipelineOperations extends OpenSearchRetryOperation {
  public OpenSearchPipelineOperations(OpenSearchClient openSearchClient,
                                      final OptimizeIndexNameService indexNameService) {
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
