/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.reader;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.webapp.rest.dto.ActivityStatisticsDto;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
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
import org.springframework.stereotype.Component;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_ARCHIVE;
import static org.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static org.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static org.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_STATE;
import static org.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_TYPE;
import static org.camunda.operate.schema.templates.ListViewTemplate.INCIDENT_KEY;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.children;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.parent;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class ActivityStatisticsReader {

  private static final Logger logger = LoggerFactory.getLogger(ActivityStatisticsReader.class);

  public static final String AGG_ACTIVITIES = "activities";
  public static final String AGG_UNIQUE_ACTIVITIES = "unique_activities";
  public static final String AGG_ACTIVITY_TO_WORKFLOW = "activity_to_workflow";
  public static final String AGG_ACTIVE_ACTIVITIES = "active_activities";
  public static final String AGG_INCIDENT_ACTIVITIES = "incident_activities";
  public static final String AGG_TERMINATED_ACTIVITIES = "terminated_activities";
  public static final String AGG_FINISHED_ACTIVITIES = "finished_activities";

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @FunctionalInterface
  private interface MapUpdater {
    void updateMapEntry(ActivityStatisticsDto statistics, Long value);
  }

  public Collection<ActivityStatisticsDto> getActivityStatistics(ListViewQueryDto query) {

    Map<String, ActivityStatisticsDto> statisticsMap = new HashMap<>();

    SearchRequest searchRequest = createQuery(query, ONLY_RUNTIME);
    runQueryAndCollectStats(statisticsMap, searchRequest);

    if (query.isFinished()) {
      searchRequest = createQuery(query, ONLY_ARCHIVE);
      runQueryAndCollectStats(statisticsMap, searchRequest);
    }

    return statisticsMap.values();
  }

  public void runQueryAndCollectStats(Map<String, ActivityStatisticsDto> statisticsMap, SearchRequest searchRequest) {
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      if (searchResponse.getAggregations() != null) {
        Children activities = searchResponse.getAggregations().get(AGG_ACTIVITIES);
        CollectionUtil.asMap(
            AGG_ACTIVE_ACTIVITIES,     (MapUpdater)ActivityStatisticsDto::addActive,
            AGG_INCIDENT_ACTIVITIES,   (MapUpdater)ActivityStatisticsDto::addIncidents,
            AGG_TERMINATED_ACTIVITIES, (MapUpdater)ActivityStatisticsDto::addCanceled,
            AGG_FINISHED_ACTIVITIES,   (MapUpdater)ActivityStatisticsDto::addCompleted)
            .forEach((aggName,mapUpdater) -> collectStatisticsFor(statisticsMap, activities, aggName, (MapUpdater)mapUpdater));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining statistics for activities: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public SearchRequest createQuery(ListViewQueryDto query, ElasticsearchUtil.QueryType queryType) {
    final QueryBuilder q = constantScoreQuery(listViewReader.createQueryFragment(query, queryType));

    ChildrenAggregationBuilder agg =
        children(AGG_ACTIVITIES, ACTIVITIES_JOIN_RELATION);

    if (queryType != ONLY_ARCHIVE && query.isActive()) {
      agg = agg.subAggregation(getActiveActivitiesAgg());
    }
    if (query.isCanceled()) {
      agg = agg.subAggregation(getTerminatedActivitiesAgg());
    }
    if (queryType != ONLY_ARCHIVE && query.isIncidents()) {
      agg = agg.subAggregation(getIncidentActivitiesAgg());
    }
    agg = agg.subAggregation(getFinishedActivitiesAgg());

    logger.debug("Activities statistics request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());

    SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType);

    logger.debug("Search request will search in: \n{}", searchRequest.indices());

    return searchRequest
        .source(new SearchSourceBuilder()
          .query(q)
          .size(0)
          .aggregation(agg));
  }

  private void collectStatisticsFor(Map<String, ActivityStatisticsDto> statisticsMap, Children activities,String aggName,MapUpdater mapUpdater) {
    Filter incidentActivitiesAgg = activities.getAggregations().get(aggName);
    if (incidentActivitiesAgg != null) {
      ((Terms) incidentActivitiesAgg.getAggregations().get(AGG_UNIQUE_ACTIVITIES)).getBuckets().stream().forEach(b -> {
        String activityId = b.getKeyAsString();
        final Parent aggregation = b.getAggregations().get(AGG_ACTIVITY_TO_WORKFLOW);
        final long docCount = aggregation.getDocCount();  //number of workflow instances
        if (statisticsMap.get(activityId) == null) {
          statisticsMap.put(activityId, new ActivityStatisticsDto(activityId));
        }
        mapUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
      });
    }
  }
  private FilterAggregationBuilder getTerminatedActivitiesAgg() {
    return filter(AGG_TERMINATED_ACTIVITIES, termQuery(ACTIVITY_STATE, FlowNodeState.TERMINATED)).subAggregation(
        terms(AGG_UNIQUE_ACTIVITIES).field(ACTIVITY_ID).size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(parent(AGG_ACTIVITY_TO_WORKFLOW, ACTIVITIES_JOIN_RELATION))
            //we need this to count workflow instances, not the activity instances
    );
  }

  private FilterAggregationBuilder getActiveActivitiesAgg() {
    return filter(AGG_ACTIVE_ACTIVITIES,
        boolQuery().mustNot(existsQuery(INCIDENT_KEY)).must(termQuery(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString()))).subAggregation(
        terms(AGG_UNIQUE_ACTIVITIES).field(ACTIVITY_ID).size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(parent(AGG_ACTIVITY_TO_WORKFLOW, ACTIVITIES_JOIN_RELATION))
            //we need this to count workflow instances, not the activity instances
    );
  }

  private FilterAggregationBuilder getIncidentActivitiesAgg() {
    return filter(AGG_INCIDENT_ACTIVITIES, existsQuery(INCIDENT_KEY)).subAggregation(
        terms(AGG_UNIQUE_ACTIVITIES).field(ACTIVITY_ID).size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(parent(AGG_ACTIVITY_TO_WORKFLOW, ACTIVITIES_JOIN_RELATION))
            //we need this to count workflow instances, not the activity instances
    );
  }

  private FilterAggregationBuilder getFinishedActivitiesAgg() {
    final QueryBuilder completedEndEventsQ = joinWithAnd(termQuery(ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString()), termQuery(ACTIVITY_STATE, FlowNodeState.COMPLETED.toString()));
    return filter(AGG_FINISHED_ACTIVITIES, completedEndEventsQ).subAggregation(
        terms(AGG_UNIQUE_ACTIVITIES).field(ACTIVITY_ID).size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(parent(AGG_ACTIVITY_TO_WORKFLOW, ACTIVITIES_JOIN_RELATION))
            //we need this to count workflow instances, not the activity instances
    );
  }

}
