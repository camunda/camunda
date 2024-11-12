/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration.util;

import static io.camunda.operate.util.ElasticsearchUtil.mapSearchHits;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

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

  public long countEntitiesFor(final String index) {
    try {
      final SearchResponse searchResponse =
          esClient.search(new SearchRequest(index), RequestOptions.DEFAULT);
      return searchResponse.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public <T> List<T> searchEntitiesFor(
      final SearchRequest searchRequest, final Class<T> entityClass) {
    applyDefaultSize(searchRequest);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return mapSearchHits(searchResponse.getHits().getHits(), objectMapper, entityClass);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public List<SearchHit> searchDocumentsFor(final SearchRequest searchRequest) {
    applyDefaultSize(searchRequest);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      return StreamSupport.stream(searchResponse.getHits().spliterator(), false)
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected void applyDefaultSize(final SearchRequest searchRequest) {
    final SearchSourceBuilder sourceBuilder = searchRequest.source();
    if (sourceBuilder.size() < 0) {
      sourceBuilder.size(1000);
    }
  }
}
