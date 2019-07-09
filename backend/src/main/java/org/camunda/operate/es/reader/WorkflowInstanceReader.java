/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.rest.dto.WorkflowInstanceCoreStatisticsDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.join.query.HasChildQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceReader.class);

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OperationReader operationReader;

  /**
   *
   * @param workflowKey
   * @return
   */
  public List<Long> queryWorkflowInstancesWithEmptyWorkflowVersion(Long workflowKey) {
      QueryBuilder queryBuilder = constantScoreQuery(
          joinWithAnd(
              termQuery(ListViewTemplate.WORKFLOW_KEY, workflowKey),
              boolQuery().mustNot(existsQuery(ListViewTemplate.WORKFLOW_VERSION))
          )
      );
      SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
                                      .source(new SearchSourceBuilder()
                                      .query(queryBuilder)
                                      .fetchSource(ListViewTemplate.WORKFLOW_KEY, null));
      try {
        return CollectionUtil.toSafeListOfLongs(ElasticsearchUtil.scrollIdsToList(searchRequest, esClient));
      } catch (IOException e) {
        final String message = String.format("Exception occurred, while obtaining workflow instance that has empty versions: %s", e.getMessage());
        logger.error(message, e);
        throw new OperateRuntimeException(message, e);
      }
  }

  /**
   * Searches for workflow instance by id.
   * @param workflowInstanceId
   * @return
   */
  public ListViewWorkflowInstanceDto getWorkflowInstanceWithOperationsById(Long workflowInstanceId) {
    final IdsQueryBuilder q = idsQuery().addIds(String.valueOf(workflowInstanceId));

    final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
      .source(new SearchSourceBuilder()
      .query(constantScoreQuery(q)));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      if (response.getHits().totalHits == 1) {
        final WorkflowInstanceForListViewEntity workflowInstance = ElasticsearchUtil
          .fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, WorkflowInstanceForListViewEntity.class);

        return ListViewWorkflowInstanceDto.createFrom(workflowInstance,
          activityInstanceWithIncidentExists(workflowInstanceId),
          operationReader.getOperations(workflowInstance.getWorkflowInstanceId()));

      } else if (response.getHits().totalHits > 1) {
        throw new NotFoundException(String.format("Could not find unique workflow instance with id '%s'.", workflowInstanceId));
      } else {
        throw new NotFoundException(String.format("Could not find workflow instance with id '%s'.", workflowInstanceId));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining workflow instance: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Searches for workflow instance by id.
   * @param workflowInstanceId
   * @return
   */
  public WorkflowInstanceForListViewEntity getWorkflowInstanceById(Long workflowInstanceId) {
    String workflowInstanceIdStr = String.valueOf(workflowInstanceId);
    final IdsQueryBuilder q = idsQuery().addIds(workflowInstanceIdStr);

    final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(q)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
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
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining workflow instance: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private boolean activityInstanceWithIncidentExists(Long workflowInstanceId) throws IOException {

    final TermQueryBuilder workflowInstanceIdQ = termQuery(ListViewTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);
    final ExistsQueryBuilder existsIncidentQ = existsQuery(ListViewTemplate.INCIDENT_KEY);

    final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(joinWithAnd(workflowInstanceIdQ, existsIncidentQ)))
        .fetchSource(ListViewTemplate.ID, null));
    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return response.getHits().getTotalHits() > 0;

  }

  public WorkflowInstanceCoreStatisticsDto getCoreStatistics() {
    final FilterAggregationBuilder incidentsAggregation = AggregationBuilders.filter("incidents",
        new HasChildQueryBuilder(ListViewTemplate.ACTIVITIES_JOIN_RELATION, QueryBuilders.existsQuery(ListViewTemplate.INCIDENT_KEY), ScoreMode.None));
    final FilterAggregationBuilder runningAggregation = AggregationBuilders.filter("running",
        QueryBuilders.termQuery(ListViewTemplate.STATE, WorkflowInstanceState.ACTIVE));
    final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
        .source(new SearchSourceBuilder().size(0).aggregation(incidentsAggregation).aggregation(runningAggregation));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      Aggregations aggregations = response.getAggregations();
      long runningCount = ((SingleBucketAggregation) aggregations.get("running")).getDocCount();
      long incidentCount = ((SingleBucketAggregation) aggregations.get("incidents")).getDocCount();
      WorkflowInstanceCoreStatisticsDto workflowInstanceCoreStatisticsDto = new WorkflowInstanceCoreStatisticsDto().setRunning(runningCount)
          .setActive(runningCount - incidentCount).setWithIncidents(incidentCount);
      return workflowInstanceCoreStatisticsDto;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining workflow instance core statistics: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}
