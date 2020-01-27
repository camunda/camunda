/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.camunda.operate.util.ElasticsearchUtil.mapSearchHits;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import java.io.IOException;
import java.util.List;

import javax.annotation.PostConstruct;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Component
@Scope(SCOPE_PROTOTYPE)
public class EntityReader {

  private String schemaVersion;

  @Autowired
  private RestHighLevelClient esClient;
  
  @Autowired
  private ObjectMapper objectMapper;
  
  public EntityReader(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }
  
  @PostConstruct
  public void init() {
    objectMapper.registerModule(new JavaTimeModule());
  }
  
  public <T> List<T> getEntitiesFor(String index,Class<T> entityClass) throws IOException{
    return searchEntitiesFor(new SearchRequest(getAliasFor(index)), entityClass);
  }
  
  protected <T> List<T> searchEntitiesFor(SearchRequest searchRequest,Class<T> entityClass) throws IOException{
    searchRequest.source().size(1000);
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return mapSearchHits(searchResponse.getHits().getHits(), objectMapper, entityClass);  
  }
  
  public String getAliasFor(String index) {
    return getAliasFor(index, schemaVersion);
  }
  
  public String getAliasFor(String index, String version) {
    if(version==null || version.isBlank()) {
      return String.format("operate-%s_alias", index);
    }else {
      return String.format("operate-%s-%s_alias", index, version);
    }
  }
}
