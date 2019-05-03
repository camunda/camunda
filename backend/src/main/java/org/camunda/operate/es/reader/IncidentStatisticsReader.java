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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
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
  
  private final AggregationBuilder countInstances = terms("workflowIds")
                                                        .field(ListViewTemplate.WORKFLOW_ID)
                                                        .size(ElasticsearchUtil.TERMS_AGG_SIZE);

  public Set<IncidentsByWorkflowGroupStatisticsDto> getIncidentStatisticsByWorkflow(){
    long start = System.currentTimeMillis();
    final Map<String, IncidentByWorkflowStatisticsDto> statByWorkflowIdMap = updateActiveInstances(getIncidentsByWorkflow());
    logger.debug(String.format("ElasticSearch query for getIncidentByWorkflowIdMap needed %d ms", (System.currentTimeMillis() - start)));
 
    final Map<String, List<WorkflowEntity>> workflowGroups = workflowReader.getWorkflowsGrouped();
    return collectStatisticsForWorkflowGroups(statByWorkflowIdMap, workflowGroups);
  }
  
  private Map<String, IncidentByWorkflowStatisticsDto> getIncidentsByWorkflow() {
    Map<String, IncidentByWorkflowStatisticsDto> results = new HashMap<>();

    QueryBuilder incidentsQuery =
        joinWithAnd(
            termQuery(ListViewTemplate.STATE, WorkflowInstanceState.ACTIVE.toString()),
            hasChildQuery(ListViewTemplate.ACTIVITIES_JOIN_RELATION, existsQuery(ListViewTemplate.INCIDENT_KEY), ScoreMode.None));

    SearchRequest searchRequest = new SearchRequest(workflowInstanceTemplate.getAlias())
        .source(new SearchSourceBuilder()
            .query(incidentsQuery)
            .aggregation(countInstances).size(0));

    try {
      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get("workflowIds")).getBuckets();
      for (Bucket bucket : buckets) {
        String workflowId = bucket.getKeyAsString();
        long incidents = bucket.getDocCount();
        results.put(workflowId, new IncidentByWorkflowStatisticsDto(workflowId,incidents, 0));
      }
      return results;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents by workflow: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
  
  private Map<String, IncidentByWorkflowStatisticsDto> updateActiveInstances(Map<String,IncidentByWorkflowStatisticsDto> statistics) {
    QueryBuilder runningInstanceQuery = joinWithAnd(
        termQuery(ListViewTemplate.STATE, WorkflowInstanceState.ACTIVE.toString()),
        termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION));
    Map<String, IncidentByWorkflowStatisticsDto> results = new HashMap<>(statistics);
    try {
      SearchRequest searchRequest = new SearchRequest(workflowInstanceTemplate.getAlias())
          .source(new SearchSourceBuilder()
              .query(runningInstanceQuery)
              .aggregation(countInstances)
              .size(0));
      
      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      
      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get("workflowIds")).getBuckets();
      for (Bucket bucket : buckets) {
        String workflowId = bucket.getKeyAsString();
        long runningCount = bucket.getDocCount();
        IncidentByWorkflowStatisticsDto statistic = results.get(workflowId);
        if (statistic != null) {
          statistic.setActiveInstancesCount(runningCount - statistic.getInstancesWithActiveIncidentsCount());
        } else {
          statistic = new IncidentByWorkflowStatisticsDto(workflowId, 0, runningCount);
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

  private Set<IncidentsByWorkflowGroupStatisticsDto> collectStatisticsForWorkflowGroups(Map<String, IncidentByWorkflowStatisticsDto> statByWorkflowIdMap,
    Map<String, List<WorkflowEntity>> workflowGroups) {
    Set<IncidentsByWorkflowGroupStatisticsDto> result = new TreeSet<>(new StatByWorkflowGroupComparator());
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
        final IncidentByWorkflowStatisticsDto statForWorkflow = statByWorkflowIdMap.get(workflowEntity.getId());
        if (statForWorkflow != null) {
          //accumulate data, even if there are no active incidents
          activeInstancesCount += statForWorkflow.getActiveInstancesCount();
          instancesWithActiveIncidentsCount += statForWorkflow.getInstancesWithActiveIncidentsCount();
          //but add to the list only those with active incidents
          if (statForWorkflow.getInstancesWithActiveIncidentsCount() > 0) {
            statForWorkflow.setName(workflowEntity.getName());
            statForWorkflow.setBpmnProcessId(workflowEntity.getBpmnProcessId());
            statForWorkflow.setVersion(workflowEntity.getVersion());
            stat.getWorkflows().add(statForWorkflow);
          }
        }
        //set the latest name
        if (workflowEntity.getVersion() > maxVersion) {
          stat.setWorkflowName(workflowEntity.getName());
          maxVersion = workflowEntity.getVersion();
        }
      }
      //if there are active incidents for a workflow group, include in the result
      if (instancesWithActiveIncidentsCount > 0) {
        stat.setActiveInstancesCount(activeInstancesCount);
        stat.setInstancesWithActiveIncidentsCount(instancesWithActiveIncidentsCount);
        result.add(stat);
      }
    }
    return result;
  }

  public Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError(){
    Set<IncidentsByErrorMsgStatisticsDto> result = new TreeSet<>(new StatByErrorMsgComparator());
    
    Map<String, WorkflowEntity> workflows = workflowReader.getWorkflowsWithFields("id","name","bpmnProcessId","version");
    
    TermsAggregationBuilder aggregation = terms("group_by_errorMessages")
        .field(IncidentTemplate.ERROR_MSG)
        .size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .subAggregation(terms("group_by_workflowIds")
            .field(IncidentTemplate.WORKFLOW_ID)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(cardinality("uniq_workflowInstances")
                .field(IncidentTemplate.WORKFLOW_INSTANCE_ID))
            .size(ElasticsearchUtil.TERMS_AGG_SIZE));

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

      IncidentByWorkflowStatisticsDto statisticForWorkflow = new IncidentByWorkflowStatisticsDto(workflowId, errorMessage, incidentsCount);
      WorkflowEntity workflow = workflows.get(workflowId);
      statisticForWorkflow.setName(workflow.getName());
      statisticForWorkflow.setBpmnProcessId(workflow.getBpmnProcessId());
      statisticForWorkflow.setVersion(workflow.getVersion());
      
      workflowStatistics.getWorkflows().add(statisticForWorkflow);
      workflowStatistics.recordInstancesCount(incidentsCount);
    }
    return workflowStatistics;
  }

  public static class StatByErrorMsgComparator implements Comparator<IncidentsByErrorMsgStatisticsDto> {
    @Override
    public int compare(IncidentsByErrorMsgStatisticsDto o1, IncidentsByErrorMsgStatisticsDto o2) {
      if (o1 == null) {
        if (o2 == null) {
          return 0;
        } else {
          return 1;
        }
      }
      if (o2 == null) {
        return -1;
      }
      if (o1.equals(o2)) {
        return 0;
      }
      int result = Long.compare(o2.getInstancesWithErrorCount(), o1.getInstancesWithErrorCount());
      if (result == 0) {
        result = o1.getErrorMessage().compareTo(o2.getErrorMessage());
      }
      return result;
    }
  }

  public static class StatByWorkflowGroupComparator implements Comparator<IncidentsByWorkflowGroupStatisticsDto> {
    @Override
    public int compare(IncidentsByWorkflowGroupStatisticsDto o1, IncidentsByWorkflowGroupStatisticsDto o2) {
      if (o1 == null) {
        if (o2 == null) {
          return 0;
        } else {
          return 1;
        }
      }
      if (o2 == null) {
        return -1;
      }
      if (o1.equals(o2)) {
        return 0;
      }
      int result = Long.compare(o2.getInstancesWithActiveIncidentsCount(), o1.getInstancesWithActiveIncidentsCount());
      if (result == 0) {
        result = o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
      }
      return result;
    }
  }
}
