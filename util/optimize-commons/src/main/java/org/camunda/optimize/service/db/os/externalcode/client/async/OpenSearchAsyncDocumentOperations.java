/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.externalcode.client.async;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;
import org.opensearch.client.opensearch.core.UpdateByQueryResponse;

public class OpenSearchAsyncDocumentOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncDocumentOperations(
      OptimizeIndexNameService indexNameService, OpenSearchAsyncClient openSearchAsyncClient) {
    super(indexNameService, openSearchAsyncClient);
  }

  public CompletableFuture<UpdateByQueryResponse> updateByQuery(
      UpdateByQueryRequest.Builder requestBuilder,
      Function<Exception, String> errorMessageSupplier) {
    requestBuilder.waitForCompletion(false);
    return safe(
        () -> openSearchAsyncClient.updateByQuery(requestBuilder.build()), errorMessageSupplier);
  }

  public CompletableFuture<DeleteByQueryResponse> deleteByQuery(
      DeleteByQueryRequest.Builder requestBuilder,
      Function<Exception, String> errorMessageSupplier) {
    requestBuilder.waitForCompletion(false);
    return safe(
        () -> openSearchAsyncClient.deleteByQuery(requestBuilder.build()), errorMessageSupplier);
  }
}
