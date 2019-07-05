/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import static org.camunda.operate.es.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
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
import org.camunda.operate.rest.dto.incidents.IncidentByWorkflowStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByWorkflowGroupStatisticsDto;
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

  private static final Logger logger = LoggerFactory.getLogger(IncidentStatisticsReader.class);

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;
  
  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private WorkflowReader workflowReader;
  
  private final AggregationBuilder countWorkflowIds = terms("workflowIds")
                                                        .field(ListViewTemplate.WORKFLOW_ID)
                                                        .size(ElasticsearchUtil.TERMS_AGG_SIZE);

  public Set<IncidentsByWorkflowGroupStatisticsDto> getWorkflowAndIncidentsStatistics(){
    final Map<Long, IncidentByWorkflowStatisticsDto> incidentsByWorkflowMap = updateActiveInstances(getIncidentsByWorkflow());
    return collectStatisticsForWorkflowGroups(incidentsByWorkflowMap);
  }

  private Map<Long, IncidentByWorkflowStatisticsDto> getIncidentsByWorkflow() {
    Map<Long, IncidentByWorkflowStatisticsDto> results = new HashMap<>();

    QueryBuilder incidentsQuery =
        joinWithAnd(
            termQuery(ListViewTemplate.STATE, WorkflowInstanceState.ACTIVE.toString()),
            hasChildQuery(ListViewTemplate.ACTIVITIES_JOIN_RELATION, existsQuery(ListViewTemplate.INCIDENT_KEY), ScoreMode.None));

    SearchRequest searchRequest = new SearchRequest(workflowInstanceTemplate.getAlias())
        .source(new SearchSourceBuilder()
            .query(incidentsQuery)
            .aggregation(countWorkflowIds).size(0));

    try {
      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get("workflowIds")).getBuckets();
      for (Bucket bucket : buckets) {
        Long workflowId = (Long) bucket.getKey();
        long incidents = bucket.getDocCount();
        results.put(workflowId, new IncidentByWorkflowStatisticsDto(workflowId.toString(),incidents, 0));
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
      SearchRequest searchRequest = new SearchRequest(workflowInstanceTemplate.getAlias())
          .source(new SearchSourceBuilder()
              .query(runningInstanceQuery)
              .aggregation(countWorkflowIds)
              .size(0));

      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get("workflowIds")).getBuckets();
      for (Bucket bucket : buckets) {
        Long workflowId = (Long)bucket.getKey();
        long runningCount = bucket.getDocCount();
        IncidentByWorkflowStatisticsDto statistic = results.get(workflowId);
        if (statistic != null) {
          statistic.setActiveInstancesCount(runningCount - statistic.getInstancesWithActiveIncidentsCount());
        } else {
          statistic = new IncidentByWorkflowStatisticsDto(workflowId.toString(), 0, runningCount);
        }
        results.put(workflowId, statistic);
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
        IncidentByWorkflowStatisticsDto statForWorkflow = incidentsByWorkflowMap.get(workflowEntity.getWorkflowId());
        if (statForWorkflow != null) {
          activeInstancesCount += statForWorkflow.getActiveInstancesCount();
          instancesWithActiveIncidentsCount += statForWorkflow.getInstancesWithActiveIncidentsCount();
        }else {
          statForWorkflow = new IncidentByWorkflowStatisticsDto(workflowEntity.getWorkflowId().toString(),0,0);
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
    
    Map<String, WorkflowEntity> workflows = workflowReader.getWorkflowsWithFields(
        WorkflowIndex.ID, WorkflowIndex.NAME, WorkflowIndex.BPMN_PROCESS_ID, WorkflowIndex.VERSION);
    
    TermsAggregationBuilder aggregation = terms("group_by_errorMessages")
        .field(IncidentTemplate.ERROR_MSG)
        .size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .subAggregation(terms("group_by_workflowIds")
            .field(IncidentTemplate.WORKFLOW_ID)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(cardinality("uniq_workflowInstances")
                .field(IncidentTemplate.WORKFLOW_INSTANCE_ID)));

    final SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias())
        .source(new SearchSourceBuilder()
            .aggregation(aggregation).size(0));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      
      Terms errorMessageAggregation = (Terms) searchResponse.getAggregations().get("group_by_errorMessages");
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

  private IncidentsByErrorMsgStatisticsDto getIncidentsByErrorMsgStatistic(Map<String, WorkflowEntity> workflows, Bucket errorMessageBucket) {
    String errorMessage = errorMessageBucket.getKeyAsString();
    
    IncidentsByErrorMsgStatisticsDto workflowStatistics = new IncidentsByErrorMsgStatisticsDto(errorMessage);
    
    Terms workflowIdAggregation = (Terms) errorMessageBucket.getAggregations().get("group_by_workflowIds");
    for (Bucket workflowIdBucket : workflowIdAggregation.getBuckets()) {
      String workflowId = workflowIdBucket.getKeyAsString();
      long incidentsCount = ((Cardinality)workflowIdBucket.getAggregations().get("uniq_workflowInstances")).getValue();

      if (workflows.containsKey(workflowId)) {
        IncidentByWorkflowStatisticsDto statisticForWorkflow = new IncidentByWorkflowStatisticsDto(workflowId, errorMessage, incidentsCount);
        WorkflowEntity workflow = workflows.get(workflowId);
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
