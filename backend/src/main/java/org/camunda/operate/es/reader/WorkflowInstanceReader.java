/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class WorkflowInstanceReader {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OperationReader operationReader;

  /**
   *
   * @param workflowId
   * @return
   */
  //TODO
//  public List<String> queryWorkflowInstancesWithEmptyWorkflowVersion(long workflowId) {
//
//    final QueryBuilder queryBuilder =
//      joinWithAnd(
//        termQuery(IncidentTemplate.WORKFLOW_ID, workflowId),
//        boolQuery()
//          .mustNot(existsQuery(IncidentTemplate.WORKFLOW_VERSION)));
////    workflow name can be null, as some workflows does not have name
//
//
//    final SearchRequestBuilder searchRequestBuilder =
//      esClient.prepareSearch(workflowInstanceTemplate.getAlias())
//        .setQuery(constantScoreQuery(queryBuilder))
//        .setFetchSource(false);
//
//    return ElasticsearchUtil.scrollIdsToList(searchRequestBuilder, esClient);
//  }

  /**
   * Searches for workflow instance by id.
   * @param workflowInstanceId
   * @return
   */
  public ListViewWorkflowInstanceDto getWorkflowInstanceWithOperationsById(String workflowInstanceId) {
    final IdsQueryBuilder q = idsQuery().addIds(workflowInstanceId);

    final SearchResponse response = esClient.prepareSearch(listViewTemplate.getAlias())
      .setQuery(q)
      .get();

    if (response.getHits().totalHits == 1) {
      final WorkflowInstanceForListViewEntity workflowInstance = ElasticsearchUtil
        .fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, WorkflowInstanceForListViewEntity.class);

      return ListViewWorkflowInstanceDto.createFrom(workflowInstance,
        activityInstanceWithIncidentExists(workflowInstanceId),
        operationReader.getOperations(workflowInstance.getId()));

    } else if (response.getHits().totalHits > 1) {
      throw new NotFoundException(String.format("Could not find unique workflow instance with id '%s'.", workflowInstanceId));
    } else {
      throw new NotFoundException(String.format("Could not find workflow instance with id '%s'.", workflowInstanceId));
    }
  }

  /**
   * Searches for workflow instance by id.
   * @param workflowInstanceId
   * @return
   */
  public WorkflowInstanceForListViewEntity getWorkflowInstanceById(String workflowInstanceId) {
    final IdsQueryBuilder q = idsQuery().addIds(workflowInstanceId);

    final SearchResponse response = esClient.prepareSearch(listViewTemplate.getAlias())
      .setQuery(q)
      .get();

    if (response.getHits().totalHits == 1) {
      final WorkflowInstanceForListViewEntity workflowInstance = ElasticsearchUtil
        .fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, WorkflowInstanceForListViewEntity.class);

      if (activityInstanceWithIncidentExists(workflowInstanceId)) {
        workflowInstance.setState(WorkflowInstanceState.INCIDENT);
      }

      return workflowInstance;
    } else if (response.getHits().totalHits > 1) {
      throw new NotFoundException(String.format("Could not find unique workflow instance with id '%s'.", workflowInstanceId));
    } else {
      throw new NotFoundException(String.format("Could not find workflow instance with id '%s'.", workflowInstanceId));
    }
  }

  private boolean activityInstanceWithIncidentExists(String workflowInstanceId) {

    final TermQueryBuilder workflowInstanceIdQ = termQuery(ListViewTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);
    final ExistsQueryBuilder existsIncidentQ = existsQuery(ListViewTemplate.INCIDENT_KEY);

    final SearchResponse response = esClient.prepareSearch(listViewTemplate.getAlias())
      .setQuery(constantScoreQuery(joinWithAnd(workflowInstanceIdQ, existsIncidentQ)))
      .setFetchSource(ListViewTemplate.ID, null)
      .get();

    return response.getHits().getTotalHits() > 0;
  }

}
