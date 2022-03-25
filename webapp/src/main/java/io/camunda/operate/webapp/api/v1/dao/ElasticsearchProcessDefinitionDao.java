/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.ProcessIndex.BPMN_XML;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("ElasticsearchProcessDefinitionDaoV1")
public class ElasticsearchProcessDefinitionDao extends ElasticsearchDao<ProcessDefinition>
    implements ProcessDefinitionDao {

  @Autowired
  private ProcessIndex processIndex;

  @Override
  public Results<ProcessDefinition> search(final Query<ProcessDefinition> query)
      throws APIException {
    logger.debug("search {}", query);
      final SearchSourceBuilder searchSourceBuilder = buildQueryOn(
          query,
          ProcessDefinition.KEY,
          new SearchSourceBuilder());
      try {
      final SearchRequest searchRequest = new SearchRequest().indices(processIndex.getAlias())
          .source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest,
          RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        return new Results<ProcessDefinition>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(ElasticsearchUtil.mapSearchHits(searchHitArray, objectMapper,
                ProcessDefinition.class))
            .setSortValues( sortValues);
      } else {
        return new Results<ProcessDefinition>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading process definitions", e);
    }
  }

  @Override
  public ProcessDefinition byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    List<ProcessDefinition> processDefinitions;
    try {
      processDefinitions = searchFor(
          new SearchSourceBuilder()
              .query(termQuery(ProcessIndex.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in reading process definition for key %s", key), e);
    }
    if (processDefinitions.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No process definition found for key %s ", key));
    }
    if (processDefinitions.size() > 1) {
      throw new ServerException(
          String.format("Found more than one process definition for key %s", key));
    }
    return processDefinitions.get(0);
  }

  @Override
  public String xmlByKey(final Long key) throws APIException {
    try {
      final SearchRequest searchRequest = new SearchRequest(processIndex.getAlias())
          .source(new SearchSourceBuilder()
              .query(termQuery(ProcessIndex.KEY, key))
              .fetchSource(BPMN_XML, null));
      final SearchResponse response = elasticsearch.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(BPMN_XML);
      }
    } catch (IOException e) {
      throw new ServerException(
          String.format("Error in reading process definition as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Process definition for key %s not found.", key));
  }

  protected void buildFiltering(final Query<ProcessDefinition> query, final SearchSourceBuilder searchSourceBuilder) {
    final ProcessDefinition filter = query.getFilter();
    if (filter != null) {
      List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(ProcessIndex.NAME, filter.getName()));
      queryBuilders.add(buildTermQuery(ProcessIndex.BPMN_PROCESS_ID, filter.getBpmnProcessId()));
      queryBuilders.add(buildTermQuery(ProcessIndex.VERSION, filter.getVersion()));
      queryBuilders.add(buildTermQuery(ProcessIndex.KEY, filter.getKey()));
      searchSourceBuilder.query(
          ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[]{})));
    }
  }

  protected List<ProcessDefinition> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(searchSource);
    return ElasticsearchUtil.scroll(searchRequest, ProcessDefinition.class, objectMapper,
        elasticsearch);
  }
}
