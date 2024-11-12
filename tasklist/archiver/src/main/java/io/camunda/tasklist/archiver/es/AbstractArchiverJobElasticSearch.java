/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.es;

import io.camunda.tasklist.archiver.AbstractArchiverJob;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class AbstractArchiverJobElasticSearch extends AbstractArchiverJob {

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  public AbstractArchiverJobElasticSearch(List<Integer> partitionIds) {
    super(partitionIds);
  }

  protected CompletableFuture<SearchResponse> sendSearchRequest(final SearchRequest searchRequest) {
    return ElasticsearchUtil.searchAsync(searchRequest, archiverExecutor, esClient);
  }
}
