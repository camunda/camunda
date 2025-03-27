/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.ProcessInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.*;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchProcessInstanceDaoV1")
public class ElasticsearchProcessInstanceDao extends ElasticsearchDao<ProcessInstance>
    implements ProcessInstanceDao {

  @Autowired private ListViewTemplate processInstanceIndex;

  @Autowired private ProcessInstanceWriter processInstanceWriter;

  private List<ProcessInstance> mapSearchHits(final SearchHit[] searchHitArray) {
    final List<ProcessInstance> processInstances =
        ElasticsearchUtil.mapSearchHits(searchHitArray, objectMapper, ProcessInstance.class);

    if (processInstances != null) {
      for (final ProcessInstance pi : processInstances) {
        pi.setStartDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getStartDate()));
        pi.setEndDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getEndDate()));
      }
    }

    return processInstances;
  }

  @Override
  public Results<ProcessInstance> search(final Query<ProcessInstance> query) throws APIException {
    logger.debug("search {}", query);
    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, ProcessInstance.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest().indices(processInstanceIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        final List<ProcessInstance> processInstances = mapSearchHits(searchHitArray);
        return new Results<ProcessInstance>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(processInstances)
            .setSortValues(sortValues);
      } else {
        return new Results<ProcessInstance>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (final Exception e) {
      throw new ServerException("Error in reading process instances", e);
    }
  }

  @Override
  public ProcessInstance byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<ProcessInstance> processInstances;
    try {
      processInstances =
          searchFor(new SearchSourceBuilder().query(termQuery(ListViewTemplate.KEY, key)));
    } catch (final Exception e) {
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
  @PreAuthorize("hasPermission('write')")
  public ChangeStatus delete(final Long key) throws APIException {
    // Check for not exists
    byKey(key);
    try {
      processInstanceWriter.deleteInstanceById(key);
      return new ChangeStatus()
          .setDeleted(1)
          .setMessage(
              String.format("Process instance and dependant data deleted for key '%s'", key));
    } catch (final IllegalArgumentException iae) {
      throw new ClientException(iae.getMessage(), iae);
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in deleting process instance and dependant data for key '%s'", key),
          e);
    }
  }

  @Override
  protected void buildFiltering(
      final Query<ProcessInstance> query, final SearchSourceBuilder searchSourceBuilder) {
    final ProcessInstance filter = query.getFilter();
    final List<QueryBuilder> queryBuilders = new ArrayList<>();
    queryBuilders.add(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    if (filter != null) {
      queryBuilders.add(buildTermQuery(ProcessInstance.KEY, filter.getKey()));
      queryBuilders.add(
          buildTermQuery(ProcessInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      queryBuilders.add(buildTermQuery(ProcessInstance.PARENT_KEY, filter.getParentKey()));
      queryBuilders.add(
          buildTermQuery(
              ProcessInstance.PARENT_FLOW_NODE_INSTANCE_KEY,
              filter.getParentFlowNodeInstanceKey()));
      queryBuilders.add(buildTermQuery(ProcessInstance.VERSION, filter.getProcessVersion()));
      queryBuilders.add(buildTermQuery(ProcessInstance.VERSION_TAG, filter.getProcessVersionTag()));
      queryBuilders.add(buildTermQuery(ProcessInstance.BPMN_PROCESS_ID, filter.getBpmnProcessId()));
      queryBuilders.add(buildTermQuery(ProcessInstance.STATE, filter.getState()));
      queryBuilders.add(buildTermQuery(ProcessInstance.INCIDENT, filter.getIncident()));
      queryBuilders.add(buildTermQuery(ProcessInstance.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildMatchDateQuery(ProcessInstance.START_DATE, filter.getStartDate()));
      queryBuilders.add(buildMatchDateQuery(ProcessInstance.END_DATE, filter.getEndDate()));
    }
    searchSourceBuilder.query(joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
  }

  protected List<ProcessInstance> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(processInstanceIndex.getAlias()).source(searchSource);
    final List<ProcessInstance> processInstances =
        tenantAwareClient.search(
            searchRequest,
            () -> {
              return ElasticsearchUtil.scroll(
                  searchRequest, ProcessInstance.class, objectMapper, elasticsearch);
            });

    if (processInstances != null) {
      for (final ProcessInstance pi : processInstances) {
        pi.setStartDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getStartDate()));
        pi.setEndDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getEndDate()));
      }
    }

    return processInstances;
  }
}
