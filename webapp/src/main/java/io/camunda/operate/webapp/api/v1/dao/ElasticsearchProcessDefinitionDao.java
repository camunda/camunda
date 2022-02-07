/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.ProcessIndex.BPMN_XML;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("ElasticsearchProcessDefinitionDaoV1")
public class ElasticsearchProcessDefinitionDao implements ProcessDefinitionDao {

  private static final Logger logger = LoggerFactory.getLogger(
      ElasticsearchProcessDefinitionDao.class);

  @Autowired
  @Qualifier("esClient")
  private RestHighLevelClient elasticsearch;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ProcessIndex processIndex;

  @Override
  public Results<ProcessDefinition> listBy(final Query<ProcessDefinition> query)
      throws APIException {
    logger.debug("listBy {}", query);
    try {
      final SearchSourceBuilder searchSourceBuilder = buildQueryOn(query,
          new SearchSourceBuilder());
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
            .setSortValues(sortValues);
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
    try {
      List<ProcessDefinition> processDefinitions = searchFor(
          new SearchSourceBuilder()
              .query(termQuery(ProcessIndex.KEY, key)));
      if (!processDefinitions.isEmpty()) {
        return processDefinitions.get(0);
      }
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in reading process definition for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("No process definition found for key %s ", key));
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

  protected SearchSourceBuilder buildQueryOn(final Query<ProcessDefinition> query,
      final SearchSourceBuilder searchSourceBuilder) {
    logger.debug("Build query for Elasticsearch from {}", query);
    buildSorting(query, searchSourceBuilder);
    buildPaging(query, searchSourceBuilder);
    buildFiltering(query, searchSourceBuilder);
    return searchSourceBuilder;
  }

  private void buildFiltering(final Query<ProcessDefinition> query,
      final SearchSourceBuilder searchSourceBuilder) {
    final ProcessDefinition example = query.getExample();
    if (example != null) {
      List<QueryBuilder> queryBuilders = new ArrayList<>();
      if (!ConversionUtils.stringIsEmpty(example.getName())) {
        queryBuilders.add(termQuery(ProcessIndex.NAME, example.getName()));
      }
      if (!ConversionUtils.stringIsEmpty(example.getBpmnProcessId())) {
        queryBuilders.add(termQuery(ProcessIndex.BPMN_PROCESS_ID, example.getBpmnProcessId()));
      }
      if (example.getVersion() != -1) {
        queryBuilders.add(termQuery(ProcessIndex.VERSION, example.getVersion()));
      }
      if (example.getKey() != -1L) {
        queryBuilders.add(termQuery(ProcessIndex.KEY, example.getKey()));
      }
      searchSourceBuilder.query(
          ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[]{})));
    }
  }

  private void buildPaging(final Query<ProcessDefinition> query,
      final SearchSourceBuilder searchSourceBuilder) {
    Object[] searchAfter = query.getSearchAfter();
    if (searchAfter != null && searchAfter.length > 0) {
      searchSourceBuilder.searchAfter(searchAfter);
    } else {
      if (query.getFrom() > 0) {
        searchSourceBuilder.from(query.getFrom());
      }
    }
    if (query.getSize() > 0) {
      searchSourceBuilder.size(query.getSize());
    }
  }

  private void buildSorting(final Query<ProcessDefinition> query,
      final SearchSourceBuilder searchSourceBuilder) {
    String by = query.getSortBy();
    String order = query.getSortOrder().name();
    if (by != null && !by.isEmpty()) {
      searchSourceBuilder
          .sort(SortBuilders.fieldSort(by).order(SortOrder.fromString(order)));
    }
    // always sort at least by key - needed for searchAfter method of paging
    searchSourceBuilder.sort(SortBuilders.fieldSort("key").order(SortOrder.ASC ));
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
