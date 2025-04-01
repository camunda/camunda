/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.children;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.parent;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITY_TYPE;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.INCIDENT;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.Convertable;
import io.camunda.operate.util.MapPath;
import io.camunda.operate.webapp.opensearch.OpenSearchQueryHelper;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsReader implements FlowNodeStatisticsReader {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private OpenSearchQueryHelper openSearchQueryHelper;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public Collection<FlowNodeStatisticsDto> getFlowNodeStatistics(final ListViewQueryDto query) {
    final SearchRequest.Builder searchRequest;

    if (!query.isFinished()) {
      searchRequest = createQuery(query, RequestDSL.QueryType.ONLY_RUNTIME);
    } else {
      searchRequest = createQuery(query, RequestDSL.QueryType.ALL);
    }

    final Map<String, FlowNodeStatisticsDto> statisticsMap = runQueryAndCollectStats(searchRequest);
    return statisticsMap.values();
  }

  private SearchRequest.Builder createQuery(
      final ListViewQueryDto query, final RequestDSL.QueryType queryType) {
    final Map<String, Aggregation> subAggregations = new HashMap<>();
    if (query.isActive()) {
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
        .query(
            withTenantCheck(
                constantScore(openSearchQueryHelper.createQueryFragment(query, queryType))))
        .size(0)
        .aggregations(
            AGG_ACTIVITIES,
            withSubaggregations(children(ACTIVITIES_JOIN_RELATION), subAggregations));
  }

  private Aggregation getTerminatedActivitiesAggregation() {
    return withSubaggregations(
        term(ACTIVITY_STATE, FlowNodeState.TERMINATED.name()), uniqueActivitiesAggregation());
  }

  private Aggregation getActiveFlowNodesAggregation() {
    return withSubaggregations(
        and(term(INCIDENT, false), term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())),
        uniqueActivitiesAggregation());
  }

  private Aggregation getIncidentActivitiesAggregation() {
    return withSubaggregations(
        and(term(INCIDENT, true), term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())),
        uniqueActivitiesAggregation());
  }

  private Aggregation getFinishedActivitiesAggregation() {
    return withSubaggregations(
        and(
            term(ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString()),
            term(ACTIVITY_STATE, FlowNodeState.COMPLETED.toString())),
        uniqueActivitiesAggregation());
  }

  private Map<String, Aggregation> uniqueActivitiesAggregation() {
    return Map.of(
        AGG_UNIQUE_ACTIVITIES,
        withSubaggregations(
            termAggregation(ACTIVITY_ID, TERMS_AGG_SIZE),
            Map.of(AGG_ACTIVITY_TO_PROCESS, parent(ACTIVITIES_JOIN_RELATION)._toAggregation())));
  }

  private Map<String, FlowNodeStatisticsDto> runQueryAndCollectStats(
      final SearchRequest.Builder searchRequest) {
    final Map<String, FlowNodeStatisticsDto> statisticsMap = new HashMap<>();
    final Map<String, Object> result = richOpenSearchClient.doc().searchAsMap(searchRequest);
    final Optional<Map<String, Object>> maybeActivities =
        MapPath.from(result)
            .getByPath("aggregations", "children#activities")
            .flatMap(Convertable::to);

    maybeActivities.ifPresent(
        activities ->
            CollectionUtil.asMap(
                    AGG_ACTIVE_ACTIVITIES,
                        (OpensearchFlowNodeStatisticsReader.MapUpdater)
                            FlowNodeStatisticsDto::addActive,
                    AGG_INCIDENT_ACTIVITIES,
                        (OpensearchFlowNodeStatisticsReader.MapUpdater)
                            FlowNodeStatisticsDto::addIncidents,
                    AGG_TERMINATED_ACTIVITIES,
                        (OpensearchFlowNodeStatisticsReader.MapUpdater)
                            FlowNodeStatisticsDto::addCanceled,
                    AGG_FINISHED_ACTIVITIES,
                        (OpensearchFlowNodeStatisticsReader.MapUpdater)
                            FlowNodeStatisticsDto::addCompleted)
                .forEach(
                    (aggName, mapUpdater) ->
                        collectStatisticsFor(
                            statisticsMap,
                            activities,
                            aggName,
                            (OpensearchFlowNodeStatisticsReader.MapUpdater) mapUpdater)));
    return statisticsMap;

    /* Original implementation */

    //    Map<String, FlowNodeStatisticsDto> statisticsMap = new HashMap<>();
    //    var aggregations = richOpenSearchClient.doc().searchAggregations(searchRequest);
    //    if (aggregations != null) {
    //      var activities = aggregations.get(AGG_ACTIVITIES);
    //      CollectionUtil.asMap(
    //        AGG_ACTIVE_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater)
    // FlowNodeStatisticsDto::addActive,
    //        AGG_INCIDENT_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater)
    // FlowNodeStatisticsDto::addIncidents,
    //        AGG_TERMINATED_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater)
    // FlowNodeStatisticsDto::addCanceled,
    //        AGG_FINISHED_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater)
    // FlowNodeStatisticsDto::addCompleted)
    //        .forEach((aggName, mapUpdater) -> collectStatisticsFor(statisticsMap, activities,
    // aggName, (OpensearchFlowNodeStatisticsReader.MapUpdater) mapUpdater));
    //    }
    //    return statisticsMap;
  }

  private void collectStatisticsFor(
      final Map<String, FlowNodeStatisticsDto> statisticsMap,
      final Map<String, Object> activities,
      final String aggName,
      final MapUpdater mapUpdater) {
    final Optional<List<Map<String, Object>>> maybeUniqueActivitiesBuckets =
        MapPath.from(activities)
            .getByPath("filter#" + aggName, "sterms#" + AGG_UNIQUE_ACTIVITIES, "buckets")
            .flatMap(Convertable::to);

    maybeUniqueActivitiesBuckets.ifPresent(
        buckets ->
            buckets.forEach(
                bucket -> {
                  final String activityId = (String) bucket.get("key");
                  final long docCount =
                      (Integer)
                          MapPath.from(bucket)
                              .getByPath("parent#" + AGG_ACTIVITY_TO_PROCESS, "doc_count")
                              .flatMap(Convertable::to)
                              .get(); // number of process instances

                  statisticsMap.putIfAbsent(activityId, new FlowNodeStatisticsDto(activityId));
                  mapUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
                }));

    /* Original implementation */

    //    var incidentsActivitiesAggregation = activities.children().aggregations().get(aggName);
    //    if(incidentsActivitiesAggregation != null){
    //        var uniqueActivities =
    // incidentsActivitiesAggregation.filter().aggregations().get(AGG_UNIQUE_ACTIVITIES);
    //        uniqueActivities.sterms().buckets().array().forEach(b -> {
    //          String activityId = b.key();
    //          var aggregation = b.aggregations().get(AGG_ACTIVITY_TO_PROCESS);
    //          final long docCount = aggregation.topHits().hits().total().value();  //number of
    // process instances
    //          if (statisticsMap.get(activityId) == null) {
    //            statisticsMap.put(activityId, new FlowNodeStatisticsDto(activityId));
    //          }
    //          mapUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
    //        });
    //    }
  }

  @FunctionalInterface
  private interface MapUpdater {
    void updateMapEntry(FlowNodeStatisticsDto statistics, Long value);
  }
}
