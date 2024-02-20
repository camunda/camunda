/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.async;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.slf4j.Logger;

public class OpenSearchAsyncIndexOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncIndexOperations(
      Logger logger, OpenSearchAsyncClient openSearchAsyncClient) {
    super(logger, openSearchAsyncClient);
  }

  public CompletableFuture<ReindexResponse> reindex(
      ReindexRequest.Builder requestBuilder, Function<Exception, String> errorMessageSupplier) {
    return safe(() -> openSearchAsyncClient.reindex(requestBuilder.build()), errorMessageSupplier);
  }
}
