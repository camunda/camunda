/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

public abstract class AbstractArchiverJobElasticSearch extends AbstractArchiverJob {

  @Autowired private RestHighLevelClient esClient;

  public AbstractArchiverJobElasticSearch(List<Integer> partitionIds) {
    super(partitionIds);
  }

  protected CompletableFuture<SearchResponse> sendSearchRequest(final SearchRequest searchRequest) {
    return ElasticsearchUtil.searchAsync(searchRequest, archiverExecutor, esClient);
  }
}
