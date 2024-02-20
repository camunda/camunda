/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.async;

import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.slf4j.Logger;

public class OpenSearchAsyncOperation
    extends io.camunda.operate.store.opensearch.client.OpenSearchOperation {
  protected OpenSearchAsyncClient openSearchAsyncClient;

  public OpenSearchAsyncOperation(Logger logger, OpenSearchAsyncClient openSearchAsyncClient) {
    super(logger);
    this.openSearchAsyncClient = openSearchAsyncClient;
  }
}
