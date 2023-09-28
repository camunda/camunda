/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.*;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.*;

import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.opensearch.OpenSearchQueryHelper;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsReader implements FlowNodeStatisticsReader {

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OpenSearchQueryHelper openSearchQueryHelper;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;


  @FunctionalInterface
  private interface MapUpdater {
    void updateMapEntry(FlowNodeStatisticsDto statistics, Long value);
  }

  @Override
  public Collection<FlowNodeStatisticsDto> getFlowNodeStatistics(ListViewQueryDto query) {
    return List.of();
    //TODO: Use a different approach - Opensearch Java client doesn't support parent aggregations
    /*
    SearchRequest.Builder searchRequest;

    if( !query.isFinished()) {
      searchRequest = createQuery(query, RequestDSL.QueryType.ONLY_RUNTIME);
    } else {
      searchRequest = createQuery(query, RequestDSL.QueryType.ALL);
    }

    Map<String, FlowNodeStatisticsDto> statisticsMap = runQueryAndCollectStats(searchRequest);
    return statisticsMap.values();

     */
  }

  private SearchRequest.Builder createQuery(ListViewQueryDto query, RequestDSL.QueryType queryType){
    Map<String, Aggregation> subAggregations = new HashMap<>();
    if (query.isActive()){
      subAggregations.put(AGG_ACTIVE_ACTIVITIES, getActiveFlowNodesAggregation());
    }
    if (query.isCanceled()) {
      subAggregations.put(AGG_TERMINATED_ACTIVITIES, getTerminatedActivitiesAggregation());
    }
    if (query.isIncidents()) {
      subAggregations.put(AGG_INCIDENT_ACTIVITIES, getIncidentActivitiesAggregation());
    }
    subAggregations.put(AGG_FINISHED_ACTIVITIES, getFinishedActivitiesAggregation());

    return searchRequestBuilder(listViewTemplate, queryType)
        .query(withTenantCheck(constantScore(openSearchQueryHelper.createQueryFragment(query, queryType))))
        .size(0)
        .aggregations(AGG_ACTIVITIES,
            withSubaggregations(children(ACTIVITIES_JOIN_RELATION), subAggregations));
  }

  private Aggregation getTerminatedActivitiesAggregation(){
    return withSubaggregations(
        term(ACTIVITY_STATE, FlowNodeState.TERMINATED.name()),
        uniqueActivitiesAggregation());
  }

  private Aggregation getActiveFlowNodesAggregation(){
    return withSubaggregations(and(
            term(INCIDENT, false),
            term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())
        ),
        uniqueActivitiesAggregation());
  }

  private Aggregation getIncidentActivitiesAggregation(){
    return withSubaggregations( and(
        term(INCIDENT, true),
        term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())
      ),
     uniqueActivitiesAggregation());
  }

  private Aggregation getFinishedActivitiesAggregation(){
    return withSubaggregations(
        and(
            term(ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString()),
            term(ACTIVITY_STATE, FlowNodeState.COMPLETED.toString())
        ),
        uniqueActivitiesAggregation());
  }

  private Map<String,Aggregation> uniqueActivitiesAggregation(){
    return  Map.of(AGG_UNIQUE_ACTIVITIES, withSubaggregations(
        termAggregation(ACTIVITY_ID, TERMS_AGG_SIZE),
        Map.of(AGG_ACTIVITY_TO_PROCESS, parent(ACTIVITIES_JOIN_RELATION)._toAggregation())));
  }

  private Map<String, FlowNodeStatisticsDto> runQueryAndCollectStats(SearchRequest.Builder searchRequest) {
    Map<String, FlowNodeStatisticsDto> statisticsMap = new HashMap<>();
    var aggregations = richOpenSearchClient.doc().searchAggregations(searchRequest);
    if (aggregations != null) {
      var activities = aggregations.get(AGG_ACTIVITIES);
      CollectionUtil.asMap(
              AGG_ACTIVE_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater) FlowNodeStatisticsDto::addActive,
              AGG_INCIDENT_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater) FlowNodeStatisticsDto::addIncidents,
              AGG_TERMINATED_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater) FlowNodeStatisticsDto::addCanceled,
              AGG_FINISHED_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater) FlowNodeStatisticsDto::addCompleted)
          .forEach((aggName, mapUpdater) -> collectStatisticsFor(statisticsMap, activities, aggName, (OpensearchFlowNodeStatisticsReader.MapUpdater) mapUpdater));
    }
    return statisticsMap;
  }

  private void collectStatisticsFor(Map<String, FlowNodeStatisticsDto> statisticsMap, Aggregate activities, String aggName, MapUpdater mapUpdater) {
    var incidentsActivitiesAggregation = activities.children().aggregations().get(aggName);
    if(incidentsActivitiesAggregation != null){
        var uniqueActivities = incidentsActivitiesAggregation.filter().aggregations().get(AGG_UNIQUE_ACTIVITIES);
        uniqueActivities.sterms().buckets().array().forEach(b -> {
          String activityId = b.key();
          var aggregation = b.aggregations().get(AGG_ACTIVITY_TO_PROCESS);
          final long docCount = aggregation.topHits().hits().total().value();  //number of process instances
          if (statisticsMap.get(activityId) == null) {
            statisticsMap.put(activityId, new FlowNodeStatisticsDto(activityId));
          }
          mapUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
        });
    }
  }
}
