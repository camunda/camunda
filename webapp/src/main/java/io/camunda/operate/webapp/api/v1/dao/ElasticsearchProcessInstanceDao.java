/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.es.writer.ProcessInstanceWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("ElasticsearchProcessInstanceDaoV1")
public class ElasticsearchProcessInstanceDao extends ElasticsearchDao<ProcessInstance>
    implements ProcessInstanceDao {
  @Autowired
  private ListViewTemplate processInstanceIndex;

  @Autowired
  private ProcessInstanceWriter processInstanceWriter;

  @Override
  public Results<ProcessInstance> search(final Query<ProcessInstance> query)
      throws APIException {
    logger.debug("search {}", query);
      final SearchSourceBuilder searchSourceBuilder = buildQueryOn(
          query,
          ProcessInstance.KEY,
          new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest = new SearchRequest().indices(
              processInstanceIndex.getAlias())
          .source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest,
          RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<ProcessInstance> processInstances =
            ElasticsearchUtil.mapSearchHits(searchHitArray, objectMapper, ProcessInstance.class);
        return new Results<ProcessInstance>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(processInstances)
            .setSortValues(sortValues);
      } else {
        return new Results<ProcessInstance>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading process instances", e);
    }
  }

  @Override
  public ProcessInstance byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    List<ProcessInstance> processInstances;
    try {
      processInstances = searchFor(
          new SearchSourceBuilder()
              .query(termQuery(ListViewTemplate.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in reading process instance for key %s", key), e);
    }
    if (processInstances.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No process instances found for key %s ", key));
    }
    if (processInstances.size() > 1) {
      throw new ServerException(
          String.format("Found more than one process instances for key %s", key));
    }
    return processInstances.get(0);
  }

  @Override
  public ChangeStatus delete(final Long key) throws APIException {
    // Check for not exists
    byKey(key);
    try {
      processInstanceWriter.deleteInstanceById(key);
      return new ChangeStatus().setDeleted(1)
          .setMessage(
              String.format( "Process instance and dependant data deleted for key '%s'", key));
    } catch(IllegalArgumentException iae){
      throw new ClientException(iae.getMessage(), iae);
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in deleting process instance and dependant data for key '%s'", key),e);
    }
  }

  protected void buildFiltering(final Query<ProcessInstance> query, final SearchSourceBuilder searchSourceBuilder) {
    final ProcessInstance filter = query.getFilter();
    List<QueryBuilder> queryBuilders = new ArrayList<>();
    queryBuilders.add(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    if (filter != null) {
      queryBuilders.add(buildTermQuery(ProcessInstance.KEY, filter.getKey()));
      queryBuilders.add(buildTermQuery(ProcessInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      queryBuilders.add(buildTermQuery(ProcessInstance.PARENT_KEY, filter.getParentKey()));
      queryBuilders.add(buildTermQuery(ProcessInstance.VERSION, filter.getProcessVersion()));
      queryBuilders.add(buildTermQuery(ProcessInstance.BPMN_PROCESS_ID, filter.getBpmnProcessId()));
      queryBuilders.add(buildTermQuery(ProcessInstance.STATE, filter.getState()));
      queryBuilders.add(buildMatchDateQuery(ProcessInstance.START_DATE, filter.getStartDate()));
      queryBuilders.add(buildMatchDateQuery(ProcessInstance.END_DATE, filter.getEndDate()));
    }
    searchSourceBuilder.query(
        joinWithAnd(queryBuilders.toArray(new QueryBuilder[]{})));
  }

  protected List<ProcessInstance> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(processInstanceIndex.getAlias())
            .source(searchSource);
    return ElasticsearchUtil.scroll(searchRequest, ProcessInstance.class, objectMapper, elasticsearch);
  }

}

