/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.reader;

import static org.camunda.operate.es.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.join.query.JoinQueryBuilders.hasChildQuery;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentByWorkflowStatisticsDto;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.camunda.operate.webapp.rest.dto.incidents.IncidentsByWorkflowGroupStatisticsDto;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.elasticsearch.search.aggregations.AggregationBuilders.*;

@Component
public class IncidentStatisticsReader extends AbstractReader {

  public static final String WORKFLOW_KEYS = "workflowKeys";

  private static final String UNIQ_WORKFLOW_INSTANCES = "uniq_workflowInstances";

  private static final String GROUP_BY_ERROR_MESSAGES = "group_by_errorMessages";

  private static final String GROUP_BY_WORKFLOW_KEYS = "group_by_workflowKeys";

  private static final Logger logger = LoggerFactory.getLogger(IncidentStatisticsReader.class);

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;
  
  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private WorkflowReader workflowReader;
  
  public static final AggregationBuilder COUNT_WORKFLOW_KEYS = terms(WORKFLOW_KEYS)
                                                        .field(ListViewTemplate.WORKFLOW_KEY)
                                                        .size(ElasticsearchUtil.TERMS_AGG_SIZE);
  
  public static final QueryBuilder INCIDENTS_QUERY =
      joinWithAnd(
          termQuery(ListViewTemplate.STATE, WorkflowInstanceState.ACTIVE.toString()),
          hasChildQuery(ListViewTemplate.ACTIVITIES_JOIN_RELATION, existsQuery(ListViewTemplate.INCIDENT_KEY), ScoreMode.None));


  public Set<IncidentsByWorkflowGroupStatisticsDto> getWorkflowAndIncidentsStatistics(){
    final Map<Long, IncidentByWorkflowStatisticsDto> incidentsByWorkflowMap = updateActiveInstances(getIncidentsByWorkflow());
    return collectStatisticsForWorkflowGroups(incidentsByWorkflowMap);
  }

  private Map<Long, IncidentByWorkflowStatisticsDto> getIncidentsByWorkflow() {
    Map<Long, IncidentByWorkflowStatisticsDto> results = new HashMap<>();

    
    SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(workflowInstanceTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(INCIDENTS_QUERY)
            .aggregation(COUNT_WORKFLOW_KEYS).size(0));

    try {
      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get(WORKFLOW_KEYS)).getBuckets();
      for (Bucket bucket : buckets) {
        Long workflowKey = (Long) bucket.getKey();
        long incidents = bucket.getDocCount();
        results.put(workflowKey, new IncidentByWorkflowStatisticsDto(workflowKey.toString(),incidents, 0));
      }
      return results;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents by workflow: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Map<Long, IncidentByWorkflowStatisticsDto> updateActiveInstances(Map<Long,IncidentByWorkflowStatisticsDto> statistics) {
    QueryBuilder runningInstanceQuery = joinWithAnd(
        termQuery(ListViewTemplate.STATE, WorkflowInstanceState.ACTIVE.toString()),
        termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION));
    Map<Long, IncidentByWorkflowStatisticsDto> results = new HashMap<>(statistics);
    try {
      SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(workflowInstanceTemplate, ONLY_RUNTIME)
          .source(new SearchSourceBuilder()
              .query(runningInstanceQuery)
              .aggregation(COUNT_WORKFLOW_KEYS)
              .size(0));

      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get(WORKFLOW_KEYS)).getBuckets();
      for (Bucket bucket : buckets) {
        Long workflowKey = (Long)bucket.getKey();
        long runningCount = bucket.getDocCount();
        IncidentByWorkflowStatisticsDto statistic = results.get(workflowKey);
        if (statistic != null) {
          statistic.setActiveInstancesCount(runningCount - statistic.getInstancesWithActiveIncidentsCount());
        } else {
          statistic = new IncidentByWorkflowStatisticsDto(workflowKey.toString(), 0, runningCount);
        }
        results.put(workflowKey, statistic);
      }
      return results;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining active workflows: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Set<IncidentsByWorkflowGroupStatisticsDto> collectStatisticsForWorkflowGroups(Map<Long, IncidentByWorkflowStatisticsDto> incidentsByWorkflowMap) {

    Set<IncidentsByWorkflowGroupStatisticsDto> result = new TreeSet<>(IncidentsByWorkflowGroupStatisticsDto.COMPARATOR);

    final Map<String, List<WorkflowEntity>> workflowGroups = workflowReader.getWorkflowsGrouped();

    //iterate over workflow groups (bpmnProcessId)
    for (Map.Entry<String, List<WorkflowEntity>> entry: workflowGroups.entrySet()) {
      IncidentsByWorkflowGroupStatisticsDto stat = new IncidentsByWorkflowGroupStatisticsDto();
      stat.setBpmnProcessId(entry.getKey());

      //accumulate stat for workflow group
      long activeInstancesCount = 0;
      long instancesWithActiveIncidentsCount = 0;

      //max version to find out latest workflow name
      long maxVersion = 0;

      //iterate over workflow versions
      for (WorkflowEntity workflowEntity: entry.getValue()) {
        IncidentByWorkflowStatisticsDto statForWorkflow = incidentsByWorkflowMap.get(workflowEntity.getKey());
        if (statForWorkflow != null) {
          activeInstancesCount += statForWorkflow.getActiveInstancesCount();
          instancesWithActiveIncidentsCount += statForWorkflow.getInstancesWithActiveIncidentsCount();
        }else {
          statForWorkflow = new IncidentByWorkflowStatisticsDto(ConversionUtils.toStringOrNull(workflowEntity.getKey()),0,0);
        }
        statForWorkflow.setName(workflowEntity.getName());
        statForWorkflow.setBpmnProcessId(workflowEntity.getBpmnProcessId());
        statForWorkflow.setVersion(workflowEntity.getVersion());
        stat.getWorkflows().add(statForWorkflow);

        //set the latest name
        if (workflowEntity.getVersion() > maxVersion) {
          stat.setWorkflowName(workflowEntity.getName());
          maxVersion = workflowEntity.getVersion();
        }
      }

      stat.setActiveInstancesCount(activeInstancesCount);
      stat.setInstancesWithActiveIncidentsCount(instancesWithActiveIncidentsCount);
      result.add(stat);
    }
    return result;
  }

  public Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError(){
    Set<IncidentsByErrorMsgStatisticsDto> result = new TreeSet<>(IncidentsByErrorMsgStatisticsDto.COMPARATOR);
    
    Map<Long, WorkflowEntity> workflows = workflowReader.getWorkflowsWithFields(
        WorkflowIndex.KEY, WorkflowIndex.NAME, WorkflowIndex.BPMN_PROCESS_ID, WorkflowIndex.VERSION);
    
    TermsAggregationBuilder aggregation = terms(GROUP_BY_ERROR_MESSAGES)
        .field(IncidentTemplate.ERROR_MSG)
        .size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .subAggregation(terms(GROUP_BY_WORKFLOW_KEYS)
            .field(IncidentTemplate.WORKFLOW_KEY)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(cardinality(UNIQ_WORKFLOW_INSTANCES)
                .field(IncidentTemplate.WORKFLOW_INSTANCE_KEY)));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .aggregation(aggregation).size(0));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      
      Terms errorMessageAggregation = (Terms) searchResponse.getAggregations().get(GROUP_BY_ERROR_MESSAGES);
      for (Bucket bucket : errorMessageAggregation.getBuckets()) {
        result.add(getIncidentsByErrorMsgStatistic(workflows, bucket));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents by error message: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
    return result;
  }

  private IncidentsByErrorMsgStatisticsDto getIncidentsByErrorMsgStatistic(Map<Long, WorkflowEntity> workflows, Bucket errorMessageBucket) {
    String errorMessage = errorMessageBucket.getKeyAsString();
    
    IncidentsByErrorMsgStatisticsDto workflowStatistics = new IncidentsByErrorMsgStatisticsDto(errorMessage);
    
    Terms workflowKeyAggregation = (Terms) errorMessageBucket.getAggregations().get(GROUP_BY_WORKFLOW_KEYS);
    for (Bucket workflowKeyBucket : workflowKeyAggregation.getBuckets()) {
      Long workflowKey = (Long)workflowKeyBucket.getKey();
      long incidentsCount = ((Cardinality)workflowKeyBucket.getAggregations().get(UNIQ_WORKFLOW_INSTANCES)).getValue();

      if (workflows.containsKey(workflowKey)) {
        IncidentByWorkflowStatisticsDto statisticForWorkflow = new IncidentByWorkflowStatisticsDto(workflowKey.toString(), errorMessage, incidentsCount);
        WorkflowEntity workflow = workflows.get(workflowKey);
        statisticForWorkflow.setName(workflow.getName());
        statisticForWorkflow.setBpmnProcessId(workflow.getBpmnProcessId());
        statisticForWorkflow.setVersion(workflow.getVersion());
        workflowStatistics.getWorkflows().add(statisticForWorkflow);
      }
      workflowStatistics.recordInstancesCount(incidentsCount);
    }
    return workflowStatistics;
  }
}
