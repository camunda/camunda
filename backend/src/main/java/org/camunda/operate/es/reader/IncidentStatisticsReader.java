/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.rest.dto.incidents.IncidentByWorkflowStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByWorkflowGroupStatisticsDto;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.join.aggregations.Children;
import org.elasticsearch.join.aggregations.Parent;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.apache.lucene.search.join.ScoreMode.None;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.INCIDENT_KEY;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.children;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.parent;
import static org.elasticsearch.join.query.JoinQueryBuilders.hasChildQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class IncidentStatisticsReader {

  private static final Logger logger = LoggerFactory.getLogger(IncidentStatisticsReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;

  @Autowired
  private WorkflowReader workflowReader;

  public Set<IncidentsByWorkflowGroupStatisticsDto> getIncidentStatisticsByWorkflow(){

    // workflowId -> incident stat
    Map<String, IncidentByWorkflowStatisticsDto> statByWorkflowIdMap = getIncidentByWorkflowIdMap();

    //workflow groups
    final Map<String, List<WorkflowEntity>> workflowGroups = workflowReader.getWorkflowsGrouped();

    return collectStatisticsForWorkflowGroups(statByWorkflowIdMap, workflowGroups);

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

  /**
   *  Returns incidents statistics by workflowId map (including zeros of instances with incidents)
   * @return
   */
  private Map<String, IncidentByWorkflowStatisticsDto> getIncidentByWorkflowIdMap() {
    Map<String, IncidentByWorkflowStatisticsDto> statByWorkflowIdMap = new HashMap<>();

    QueryBuilder activeInstancesQ = termQuery(IncidentTemplate.STATE, WorkflowInstanceState.ACTIVE.toString());

    final String workflowIdsAggName = "workflowIds";
    final String activitiesAggName = "activities";
    final String incidentActivitiesAggName = "incident_activities";
    final String activityToInstanceAggName = "activity_to_instances";

    final AggregationBuilder agg =
      terms(workflowIdsAggName)
        .field(ListViewTemplate.WORKFLOW_ID)
        .size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .subAggregation(
          children(activitiesAggName, ListViewTemplate.ACTIVITIES_JOIN_RELATION)
            .subAggregation(filter(incidentActivitiesAggName, existsQuery(INCIDENT_KEY))
                .subAggregation(parent(activityToInstanceAggName, ACTIVITIES_JOIN_RELATION))    //we need this to count workflow instances, not the activity instances
            )
        );

    logger.debug("Incident by workflow statistics query: \n{}\n and aggregation: \n{}", activeInstancesQ.toString(), agg.toString());

    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowInstanceTemplate.getAlias())
        .setSize(0)
        .setQuery(activeInstancesQ)
        .addAggregation(agg);

    final SearchResponse searchResponse = searchRequestBuilder.get();

    ((Terms)searchResponse.getAggregations().get(workflowIdsAggName))
      .getBuckets().forEach(b -> {
        String workflowId = (String)b.getKey();
        final long runningInstancesCount = b.getDocCount();

        final long instancesWithIncidentsCount =
          ((Parent)
            ((Filter)
              ((Children) b.getAggregations().get(activitiesAggName)).getAggregations()
                .get(incidentActivitiesAggName)).getAggregations()
                  .get(activityToInstanceAggName)).getDocCount();

        final IncidentByWorkflowStatisticsDto incidentByWorkflowStat = new IncidentByWorkflowStatisticsDto(workflowId, instancesWithIncidentsCount,
          runningInstancesCount - instancesWithIncidentsCount);
      statByWorkflowIdMap.put(workflowId, incidentByWorkflowStat);
    });
    return statByWorkflowIdMap;
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

    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowInstanceTemplate.getAlias())
        .setSize(0)
        .setQuery(withIncidentsQ)
        .addAggregation(agg);

    final SearchResponse searchResponse = searchRequestBuilder.get();


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
