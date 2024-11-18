/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client.sync;

import io.camunda.optimize.service.db.os.client.OpenSearchOperation;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchClient;

public class OpenSearchSyncOperation extends OpenSearchOperation {

  protected OpenSearchClient openSearchClient;

  public OpenSearchSyncOperation(
      final OpenSearchClient openSearchClient, final OptimizeIndexNameService indexNameService) {
    super(indexNameService);
    this.openSearchClient = openSearchClient;
  }
}
