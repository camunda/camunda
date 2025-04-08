/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.operate.store.opensearch.client.OpenSearchOperation;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;

public class OpenSearchSyncOperation extends OpenSearchOperation {
  protected OpenSearchClient openSearchClient;

  public OpenSearchSyncOperation(final Logger logger, final OpenSearchClient openSearchClient) {
    super(logger);
    this.openSearchClient = openSearchClient;
  }

  protected <R> R withExtendedOpenSearchClient(final Function<ExtendedOpenSearchClient, R> f) {
    if (openSearchClient instanceof final ExtendedOpenSearchClient extendedOpenSearchClient) {
      return f.apply(extendedOpenSearchClient);
    } else {
      throw new UnsupportedOperationException(
          "ExtendedOpenSearchClient is required! Provided: "
              + openSearchClient.getClass().getName());
    }
  }
}
