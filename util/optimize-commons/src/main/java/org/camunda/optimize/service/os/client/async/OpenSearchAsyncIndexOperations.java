/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.client.async;

import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class OpenSearchAsyncIndexOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncIndexOperations(OpenSearchAsyncClient openSearchAsyncClient,
                                        OptimizeIndexNameService indexNameService) {
    super(openSearchAsyncClient, indexNameService);
  }

  public CompletableFuture<ReindexResponse> reindex(ReindexRequest.Builder requestBuilder,
                                                    Function<Exception, String> errorMessageSupplier) {
    return safe(() -> openSearchAsyncClient.reindex(requestBuilder.build()), errorMessageSupplier);
  }
}
