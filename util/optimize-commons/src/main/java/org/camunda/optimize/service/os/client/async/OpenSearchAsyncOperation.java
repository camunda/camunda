/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.client.async;

import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.os.OpenSearchOperation;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;

public class OpenSearchAsyncOperation extends OpenSearchOperation {
  protected OpenSearchAsyncClient openSearchAsyncClient;

  public OpenSearchAsyncOperation(OpenSearchAsyncClient openSearchAsyncClient,
                                  OptimizeIndexNameService indexNameService) {
    super(indexNameService);
    this.openSearchAsyncClient = openSearchAsyncClient;
  }
}
