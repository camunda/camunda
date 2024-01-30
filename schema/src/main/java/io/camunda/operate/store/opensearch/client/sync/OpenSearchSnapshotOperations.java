/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client.sync;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.GetRepositoryResponse;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.GetSnapshotResponse;
import org.slf4j.Logger;

import java.io.IOException;

public class OpenSearchSnapshotOperations extends OpenSearchSyncOperation {
  public OpenSearchSnapshotOperations(Logger logger, OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  public GetRepositoryResponse getRepository(GetRepositoryRequest.Builder requestBuilder) throws IOException {
    return openSearchClient.snapshot().getRepository(requestBuilder.build());
  }

  public GetSnapshotResponse get(GetSnapshotRequest.Builder requestBuilder) throws IOException {
    return openSearchClient.snapshot().get(requestBuilder.build());
  }
}
