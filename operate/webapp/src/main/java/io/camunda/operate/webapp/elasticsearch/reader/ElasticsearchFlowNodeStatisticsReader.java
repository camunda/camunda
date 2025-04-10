/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_TYPE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.children;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.parent;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.elasticsearch.QueryHelper;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.join.aggregations.Children;
import org.elasticsearch.join.aggregations.ChildrenAggregationBuilder;
import org.elasticsearch.join.aggregations.Parent;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchFlowNodeStatisticsReader implements FlowNodeStatisticsReader {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchFlowNodeStatisticsReader.class);

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private QueryHelper queryHelper;

  @Override
  public Collection<FlowNodeStatisticsDto> getFlowNodeStatistics(final ListViewQueryDto query) {

    final SearchRequest searchRequest;
    if (!query.isFinished()) {
      searchRequest = createQuery(query, ONLY_RUNTIME);
    } else {
      searchRequest = createQuery(query, ALL);
    }
    final Map<String, FlowNodeStatisticsDto> statisticsMap = runQueryAndCollectStats(searchRequest);
    return statisticsMap.values();
  }

  private Map<String, FlowNodeStatisticsDto> runQueryAndCollectStats(
      final SearchRequest searchRequest) {
    try {
      final Map<String, FlowNodeStatisticsDto> statisticsMap = new HashMap<>();
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      if (searchResponse.getAggregations() != null) {
        final Children activities = searchResponse.getAggregations().get(AGG_ACTIVITIES);
        CollectionUtil.asMap(
                AGG_ACTIVE_ACTIVITIES, (MapUpdater) FlowNodeStatisticsDto::addActive,
                AGG_INCIDENT_ACTIVITIES, (MapUpdater) FlowNodeStatisticsDto::addIncidents,
                AGG_TERMINATED_ACTIVITIES, (MapUpdater) FlowNodeStatisticsDto::addCanceled,
                AGG_FINISHED_ACTIVITIES, (MapUpdater) FlowNodeStatisticsDto::addCompleted)
            .forEach(
                (aggName, mapUpdater) ->
                    collectStatisticsFor(
                        statisticsMap, activities, aggName, (MapUpdater) mapUpdater));
      }
      return statisticsMap;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining statistics for activities: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private SearchRequest createQuery(
      final ListViewQueryDto query, final ElasticsearchUtil.QueryType queryType) {
    final QueryBuilder q = constantScoreQuery(queryHelper.createQueryFragment(query));

    ChildrenAggregationBuilder agg = children(AGG_ACTIVITIES, ACTIVITIES_JOIN_RELATION);

    if (query.isActive()) {
      agg = agg.subAggregation(getActiveFlowNodesAgg());
    }
    if (query.isCanceled()) {
      agg = agg.subAggregation(getTerminatedActivitiesAgg());
    }
    if (query.isIncidents()) {
      agg = agg.subAggregation(getIncidentActivitiesAgg());
    }
    agg = agg.subAggregation(getFinishedActivitiesAgg());

    LOGGER.debug("Activities statistics request: \n{}\n and aggregation: \n{}", q, agg);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType);

    LOGGER.debug("Search request will search in: \n{}", searchRequest.indices());

    return searchRequest.source(new SearchSourceBuilder().query(q).size(0).aggregation(agg));
  }

  private void collectStatisticsFor(
      final Map<String, FlowNodeStatisticsDto> statisticsMap,
      final Children activities,
      final String aggName,
      final MapUpdater mapUpdater) {
    final Filter incidentActivitiesAgg = activities.getAggregations().get(aggName);
    if (incidentActivitiesAgg != null) {
      ((Terms) incidentActivitiesAgg.getAggregations().get(AGG_UNIQUE_ACTIVITIES))
          .getBuckets().stream()
              .forEach(
                  b -> {
                    final String activityId = b.getKeyAsString();
                    final Parent aggregation = b.getAggregations().get(AGG_ACTIVITY_TO_PROCESS);
                    final long docCount = aggregation.getDocCount(); // number of process instances
                    if (statisticsMap.get(activityId) == null) {
                      statisticsMap.put(activityId, new FlowNodeStatisticsDto(activityId));
                    }
                    mapUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
                  });
    }
  }

  private FilterAggregationBuilder getTerminatedActivitiesAgg() {
    return filter(AGG_TERMINATED_ACTIVITIES, termQuery(ACTIVITY_STATE, FlowNodeState.TERMINATED))
        .subAggregation(
            terms(AGG_UNIQUE_ACTIVITIES)
                .field(ACTIVITY_ID)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                .subAggregation(parent(AGG_ACTIVITY_TO_PROCESS, ACTIVITIES_JOIN_RELATION))
            // we need this to count process instances, not the activity instances
            );
  }

  private FilterAggregationBuilder getActiveFlowNodesAgg() {
    return filter(
            AGG_ACTIVE_ACTIVITIES,
            boolQuery()
                .must(termQuery(INCIDENT, false))
                .must(termQuery(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
        .subAggregation(
            terms(AGG_UNIQUE_ACTIVITIES)
                .field(ACTIVITY_ID)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                .subAggregation(parent(AGG_ACTIVITY_TO_PROCESS, ACTIVITIES_JOIN_RELATION))
            // we need this to count process instances, not the activity instances
            );
  }

  private FilterAggregationBuilder getIncidentActivitiesAgg() {
    return filter(
            AGG_INCIDENT_ACTIVITIES,
            boolQuery()
                .must(termQuery(INCIDENT, true))
                .must(termQuery(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
        .subAggregation(
            terms(AGG_UNIQUE_ACTIVITIES)
                .field(ACTIVITY_ID)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                .subAggregation(parent(AGG_ACTIVITY_TO_PROCESS, ACTIVITIES_JOIN_RELATION))
            // we need this to count process instances, not the activity instances
            );
  }

  private FilterAggregationBuilder getFinishedActivitiesAgg() {
    final QueryBuilder completedEndEventsQ =
        joinWithAnd(
            termQuery(ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString()),
            termQuery(ACTIVITY_STATE, FlowNodeState.COMPLETED.toString()));
    return filter(AGG_FINISHED_ACTIVITIES, completedEndEventsQ)
        .subAggregation(
            terms(AGG_UNIQUE_ACTIVITIES)
                .field(ACTIVITY_ID)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                .subAggregation(parent(AGG_ACTIVITY_TO_PROCESS, ACTIVITIES_JOIN_RELATION))
            // we need this to count process instances, not the activity instances
            );
  }

  @FunctionalInterface
  private interface MapUpdater {
    void updateMapEntry(FlowNodeStatisticsDto statistics, Long value);
  }
}
