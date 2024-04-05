/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver.os;

import io.camunda.tasklist.archiver.AbstractArchiverJob;
import io.camunda.tasklist.util.OpenSearchUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractArchiverJobOpenSearch extends AbstractArchiverJob {

  @Autowired private OpenSearchAsyncClient osAsyncClient;

  public AbstractArchiverJobOpenSearch(List<Integer> partitionIds) {
    super(partitionIds);
  }

  protected CompletableFuture<SearchResponse<Object>> sendSearchRequest(
      final SearchRequest searchRequest) {
    return OpenSearchUtil.searchAsync(searchRequest, archiverExecutor, osAsyncClient);
  }
}
