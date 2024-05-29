/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
