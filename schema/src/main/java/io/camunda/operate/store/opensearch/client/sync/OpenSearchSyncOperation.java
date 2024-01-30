/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.operate.store.opensearch.client.OpenSearchOperation;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;

import java.util.function.Function;

public class OpenSearchSyncOperation extends OpenSearchOperation {
  protected OpenSearchClient openSearchClient;

  public OpenSearchSyncOperation(Logger logger, OpenSearchClient openSearchClient) {
    super(logger);
    this.openSearchClient = openSearchClient;
  }

  protected <R> R withExtendedOpenSearchClient(Function<ExtendedOpenSearchClient, R> f) {
    if (openSearchClient instanceof ExtendedOpenSearchClient extendedOpenSearchClient) {
      return f.apply(extendedOpenSearchClient);
    } else {
      throw new UnsupportedOperationException("ExtendedOpenSearchClient is required! Provided: " + openSearchClient.getClass().getName());
    }
  }
}
