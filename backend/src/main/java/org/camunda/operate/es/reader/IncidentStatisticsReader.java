/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import static org.apache.lucene.search.join.ScoreMode.None;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.INCIDENT_KEY;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.children;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.parent;
import static org.elasticsearch.join.query.JoinQueryBuilders.hasChildQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

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
import org.elasticsearch.join.aggregations.Children;
import org.elasticsearch.join.aggregations.Parent;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IncidentStatisticsReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(IncidentStatisticsReader.class);

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;

  @Autowired
  private WorkflowReader workflowReader;
  
  private final AggregationBuilder countInstances = AggregationBuilders.terms("workflowIds")
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
    Map<String, IncidentByWorkflowStatisticsDto> results = new HashMap<String, IncidentByWorkflowStatisticsDto>();

    QueryBuilder incidentsQuery = boolQuery().must(
        ElasticsearchUtil.joinWithAnd(
            termQuery(IncidentTemplate.STATE, WorkflowInstanceState.ACTIVE.toString()),
            hasChildQuery(ListViewTemplate.ACTIVITIES_JOIN_RELATION, existsQuery(ListViewTemplate.INCIDENT_KEY), ScoreMode.None)));

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
    QueryBuilder runningInstanceQuery = termQuery(IncidentTemplate.STATE, WorkflowInstanceState.ACTIVE.toString());
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

  public Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError() {

    Map<String, WorkflowEntity> workflows = workflowReader.getWorkflows();

    final QueryBuilder withIncidentsQ = hasChildQuery(ACTIVITIES_JOIN_RELATION, existsQuery(INCIDENT_KEY), None);

    final String activitiesAggName = "activities";
    final String incidentActivitiesAggName = "incident_activities";
    final String errorMessagesAggName = "errorMessages";
    final String activityToInstanceAggName = "activity_to_instances";
    final String workflowIdsAggName = "workflowIds";


    AggregationBuilder agg =
      children(activitiesAggName, ListViewTemplate.ACTIVITIES_JOIN_RELATION)
        .subAggregation(filter(incidentActivitiesAggName, existsQuery(INCIDENT_KEY)).subAggregation(
          terms(errorMessagesAggName)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .field(ListViewTemplate.ERROR_MSG).subAggregation(
              parent(activityToInstanceAggName, ACTIVITIES_JOIN_RELATION).subAggregation(
                terms(workflowIdsAggName)
                  .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                  .field(ListViewTemplate.WORKFLOW_ID)     //TODO check if we can put workflowId inside incident entity
            )
          )
        )
      );

    logger.debug("Incident by error message statistics query: \n{}\n and aggregation: \n{}", withIncidentsQ.toString(), agg.toString());

    final SearchRequest searchRequest = new SearchRequest(workflowInstanceTemplate.getAlias())
      .source(new SearchSourceBuilder()
        .query(withIncidentsQ)
        .aggregation(agg)
        .size(0));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      Set<IncidentsByErrorMsgStatisticsDto> result = new TreeSet<>(new StatByErrorMsgComparator());

      ((Terms)
        ((Filter)
          ((Children)searchResponse.getAggregations().get(activitiesAggName))
            .getAggregations().get(incidentActivitiesAggName))
              .getAggregations().get(errorMessagesAggName)).getBuckets().forEach(o -> {

          IncidentsByErrorMsgStatisticsDto incidentsByErrorMsgStat = new IncidentsByErrorMsgStatisticsDto(o.getKeyAsString());
          ((Terms)
            ((Parent)o.getAggregations().get(activityToInstanceAggName))
              .getAggregations().get(workflowIdsAggName)).getBuckets().forEach(w -> {

              final String workflowId = w.getKeyAsString();
              IncidentByWorkflowStatisticsDto statForWorkflowId = new IncidentByWorkflowStatisticsDto(workflowId, o.getKeyAsString(), w.getDocCount());
              statForWorkflowId.setName(workflows.get(workflowId).getName());
              statForWorkflowId.setBpmnProcessId(workflows.get(workflowId).getBpmnProcessId());
              statForWorkflowId.setVersion(workflows.get(workflowId).getVersion());
              incidentsByErrorMsgStat.getWorkflows().add(statForWorkflowId);
              incidentsByErrorMsgStat.recordInstancesCount(w.getDocCount());
            }
          );
        result.add(incidentsByErrorMsgStat);

      });

      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents by error message: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
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
