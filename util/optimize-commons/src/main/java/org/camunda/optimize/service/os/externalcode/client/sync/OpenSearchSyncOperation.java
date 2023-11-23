/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.externalcode.client.sync;

import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.os.externalcode.OpenSearchOperation;
import org.opensearch.client.opensearch.OpenSearchClient;

public class OpenSearchSyncOperation extends OpenSearchOperation {
  protected OpenSearchClient openSearchClient;

  public OpenSearchSyncOperation(OpenSearchClient openSearchClient,
                                 final OptimizeIndexNameService indexNameService) {
    super(indexNameService);
    this.openSearchClient = openSearchClient;
  }
}
