/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration.util;

import static org.camunda.operate.util.ElasticsearchUtil.mapSearchHits;
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

  private String schemaVersion;

  private RestHighLevelClient esClient;

  private ObjectMapper objectMapper = new ObjectMapper();

  public EntityReader(RestHighLevelClient esClient, String schemaVersion) {
    this.esClient = esClient;
    this.schemaVersion = schemaVersion;
    init();
  }

  public void init() {
    objectMapper.registerModule(new JavaTimeModule());
  }

  public <T> List<T> getEntitiesFor(String index, Class<T> entityClass) {
    return searchEntitiesFor(new SearchRequest(getAliasFor(index)), entityClass);
  }

  public <T> List<T> searchEntitiesFor(SearchRequest searchRequest, Class<T> entityClass) {
    searchRequest.source().size(1000);
    SearchResponse searchResponse = null;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return mapSearchHits(searchResponse.getHits().getHits(), objectMapper, entityClass);
  }

  public String getAliasFor(String index) {
    return getAliasFor(index, schemaVersion.toLowerCase());
  }

  public String getAliasFor(String index, String version) {
    if (version == null || version.isBlank()) {
      return String.format("operate-%s_alias", index);
    } else {
      return String.format("operate-%s-%s_alias", index, version);
    }
  }
}
