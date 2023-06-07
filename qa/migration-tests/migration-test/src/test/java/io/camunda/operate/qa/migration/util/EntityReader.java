/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration.util;

import static io.camunda.operate.util.ElasticsearchUtil.mapSearchHits;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class EntityReader {

  private RestHighLevelClient esClient;

  private ObjectMapper objectMapper = new ObjectMapper();

  public EntityReader(RestHighLevelClient esClient) {
    this.esClient = esClient;
    init();
  }

  public void init() {
    objectMapper.registerModule(new JavaTimeModule());
  }

  public <T> List<T> getEntitiesFor(String index, Class<T> entityClass) {
    return searchEntitiesFor(new SearchRequest(index), entityClass);
  }

  public long countEntitiesFor(String index) {
    try {
      SearchResponse searchResponse = esClient.search(new SearchRequest(index), RequestOptions.DEFAULT);
      return searchResponse.getHits().getTotalHits().value;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public <T> List<T> searchEntitiesFor(SearchRequest searchRequest, Class<T> entityClass) {
    searchRequest.source().size(1000);
    try {
      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return mapSearchHits(searchResponse.getHits().getHits(), objectMapper, entityClass);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
