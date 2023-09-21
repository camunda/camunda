/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;

public class OpenSearchPipelineOperations extends OpenSearchRetryOperation {
  public OpenSearchPipelineOperations(Logger logger, OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  public boolean addPipelineWithRetries(String name, String definition) {
    // final BytesReference content = new BytesArray(definition.getBytes());
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
