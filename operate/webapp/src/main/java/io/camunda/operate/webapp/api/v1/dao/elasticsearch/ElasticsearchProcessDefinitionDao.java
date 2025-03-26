/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_XML;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchProcessDefinitionDaoV1")
public class ElasticsearchProcessDefinitionDao extends ElasticsearchDao<ProcessDefinition>
    implements ProcessDefinitionDao {

  @Autowired
  @Qualifier("operateProcessIndex")
  private ProcessIndex processIndex;

  @Override
  public Results<ProcessDefinition> search(final Query<ProcessDefinition> query)
      throws APIException {
    logger.debug("search {}", query);
    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, ProcessDefinition.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest().indices(processIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        return new Results<ProcessDefinition>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(
                ElasticsearchUtil.mapSearchHits(
                    searchHitArray, objectMapper, ProcessDefinition.class))
            .setSortValues(sortValues);
      } else {
        return new Results<ProcessDefinition>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (final Exception e) {
      throw new ServerException("Error in reading process definitions", e);
    }
  }

  @Override
  public ProcessDefinition byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<ProcessDefinition> processDefinitions;
    try {
      processDefinitions =
          searchFor(new SearchSourceBuilder().query(termQuery(ProcessIndex.KEY, key)));
    } catch (final Exception e) {
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
      final SearchRequest searchRequest =
          new SearchRequest(processIndex.getAlias())
              .source(
                  new SearchSourceBuilder()
                      .query(termQuery(ProcessIndex.KEY, key))
                      .fetchSource(BPMN_XML, null));
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        final Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(BPMN_XML);
      }
    } catch (final IOException e) {
      throw new ServerException(
          String.format("Error in reading process definition as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Process definition for key %s not found.", key));
  }

  @Override
  protected void buildFiltering(
      final Query<ProcessDefinition> query, final SearchSourceBuilder searchSourceBuilder) {
    final ProcessDefinition filter = query.getFilter();
    if (filter != null) {
      final List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(ProcessDefinition.NAME, filter.getName()));
      queryBuilders.add(
          buildTermQuery(ProcessDefinition.BPMN_PROCESS_ID, filter.getBpmnProcessId()));
      queryBuilders.add(buildTermQuery(ProcessDefinition.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildTermQuery(ProcessDefinition.VERSION, filter.getVersion()));
      queryBuilders.add(buildTermQuery(ProcessDefinition.KEY, filter.getKey()));
      searchSourceBuilder.query(
          ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
    }
  }

  protected List<ProcessDefinition> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias()).source(searchSource);
    return tenantAwareClient.search(
        searchRequest,
        () -> {
          return ElasticsearchUtil.scroll(
              searchRequest, ProcessDefinition.class, objectMapper, elasticsearch);
        });
  }
}
