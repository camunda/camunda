/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class AbstractArchiverJobOpenSearch extends AbstractArchiverJob {

  @Autowired
  @Qualifier("tasklistOsAsyncClient")
  private OpenSearchAsyncClient osAsyncClient;

  public AbstractArchiverJobOpenSearch(List<Integer> partitionIds) {
    super(partitionIds);
  }

  protected CompletableFuture<SearchResponse<Object>> sendSearchRequest(
      final SearchRequest searchRequest) {
    return OpenSearchUtil.searchAsync(searchRequest, archiverExecutor, osAsyncClient);
  }
}
