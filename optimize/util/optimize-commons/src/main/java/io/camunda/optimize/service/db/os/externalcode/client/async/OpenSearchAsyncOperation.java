/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.externalcode.client.async;

import io.camunda.optimize.service.db.os.externalcode.OpenSearchOperation;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;

public class OpenSearchAsyncOperation extends OpenSearchOperation {
  protected OpenSearchAsyncClient openSearchAsyncClient;

  public OpenSearchAsyncOperation(
      OptimizeIndexNameService indexNameService, OpenSearchAsyncClient openSearchAsyncClient) {
    super(indexNameService);
    this.openSearchAsyncClient = openSearchAsyncClient;
  }
}
