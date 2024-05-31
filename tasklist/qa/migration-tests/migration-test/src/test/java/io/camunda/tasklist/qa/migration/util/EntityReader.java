/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.util;

import static io.camunda.tasklist.util.ElasticsearchUtil.mapSearchHits;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;

public class EntityReader {

  private final RestHighLevelClient esClient;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public EntityReader(final RestHighLevelClient esClient) {
    this.esClient = esClient;
    init();
  }

  public void init() {
    objectMapper.registerModule(new JavaTimeModule());
  }

  public <T> List<T> getEntitiesFor(final String index, final Class<T> entityClass) {
    return searchEntitiesFor(new SearchRequest(index), entityClass);
  }

  public <T> List<T> searchEntitiesFor(
      final SearchRequest searchRequest, final Class<T> entityClass) {
    searchRequest.source().size(1000);
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return mapSearchHits(searchResponse.getHits().getHits(), objectMapper, entityClass);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public List<SearchHit> searchDocumentsFor(final SearchRequest searchRequest) {
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return List.of(searchResponse.getHits().getHits());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
