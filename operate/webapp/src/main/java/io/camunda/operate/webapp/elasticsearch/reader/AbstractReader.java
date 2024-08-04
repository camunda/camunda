/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class AbstractReader {

  @Autowired protected RestHighLevelClient esClient;

  @Autowired protected TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  protected <T extends OperateEntity> List<T> scroll(SearchRequest searchRequest, Class<T> clazz)
      throws IOException {
    return tenantAwareClient.search(
        searchRequest,
        () -> {
          return ElasticsearchUtil.scroll(searchRequest, clazz, objectMapper, esClient);
        });
  }

  protected <T extends OperateEntity> List<T> scroll(
      SearchRequest searchRequest, Class<T> clazz, Consumer<Aggregations> aggsProcessor)
      throws IOException {
    return tenantAwareClient.search(
        searchRequest,
        () -> {
          return ElasticsearchUtil.scroll(
              searchRequest, clazz, objectMapper, esClient, null, aggsProcessor);
        });
  }

  protected <T extends OperateEntity> List<T> scroll(
      SearchRequest searchRequest,
      Class<T> clazz,
      Consumer<SearchHits> searchHitsProcessor,
      Consumer<Aggregations> aggsProcessor)
      throws IOException {
    return tenantAwareClient.search(
        searchRequest,
        () -> {
          return ElasticsearchUtil.scroll(
              searchRequest, clazz, objectMapper, esClient, searchHitsProcessor, aggsProcessor);
        });
  }
}
