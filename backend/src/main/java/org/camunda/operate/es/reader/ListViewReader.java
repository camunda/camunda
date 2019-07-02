/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.ActivityStatisticsDto;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.dto.listview.VariablesQueryDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.join.aggregations.Children;
import org.elasticsearch.join.aggregations.ChildrenAggregationBuilder;
import org.elasticsearch.join.aggregations.Parent;
import org.elasticsearch.join.query.HasChildQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.apache.lucene.search.join.ScoreMode.None;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ACTIVITY_STATE;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ACTIVITY_TYPE;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.END_DATE;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.ERROR_MSG;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.INCIDENT_KEY;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.STATE;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.VARIABLES_JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.VAR_NAME;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.VAR_VALUE;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.ElasticsearchUtil.createMatchNoneQuery;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.children;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.parent;
import static org.elasticsearch.join.query.JoinQueryBuilders.hasChildQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class ListViewReader {

  private static final Logger logger = LoggerFactory.getLogger(ListViewReader.class);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private OperationReader operationReader;

  /**
   * Queries workflow instances by different criteria (with pagination).
   * @param workflowInstanceRequest
   * @param firstResult
   * @param maxResults
   * @return
   */
  public ListViewResponseDto queryWorkflowInstances(ListViewRequestDto workflowInstanceRequest, Integer firstResult, Integer maxResults) {
    ListViewResponseDto result = new ListViewResponseDto();

    List<WorkflowInstanceForListViewEntity> workflowInstanceEntities = queryListView(workflowInstanceRequest, firstResult, maxResults, result);
    List<Long> ids = workflowInstanceEntities.stream().collect(ArrayList::new, (list, hit) -> list.add(Long.valueOf(hit.getId())), (list1, list2) -> list1.addAll(list2));

    final Set<Long> instancesWithIncidentsIds = findInstancesWithIncidents(ids);

    final Map<Long, List<OperationEntity>> operationsPerWorfklowInstance = operationReader.getOperationsPerWorkflowInstanceId(ids);

    final List<ListViewWorkflowInstanceDto> workflowInstanceDtoList = ListViewWorkflowInstanceDto
      .createFrom(workflowInstanceEntities, instancesWithIncidentsIds, operationsPerWorfklowInstance);
    result.setWorkflowInstances(workflowInstanceDtoList);
    return result;

  }

  protected List<WorkflowInstanceForListViewEntity> queryListView(ListViewRequestDto workflowInstanceRequest,
    Integer firstResult, Integer maxResults, ListViewResponseDto result) {
    SearchSourceBuilder searchSourceBuilder = createSearchSourceBuilder(workflowInstanceRequest);
    searchSourceBuilder
      .from(firstResult)
      .size(maxResults);
    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    searchRequest.source(searchSourceBuilder);
    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      result.setTotalCount(response.getHits().getTotalHits());
      return ElasticsearchUtil
        .mapSearchHits(response.getHits().getHits(), objectMapper, WorkflowInstanceForListViewEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining instances list: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public SearchSourceBuilder createSearchSourceBuilder(ListViewRequestDto request) {

    final QueryBuilder query = createRequestQuery(request);

    ConstantScoreQueryBuilder constantScoreQuery = constantScoreQuery(query);

    logger.debug("Workflow instance search request: \n{}", constantScoreQuery.toString());

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(constantScoreQuery);
    applySorting(searchSourceBuilder, request.getSorting());
    return searchSourceBuilder;
  }

  private void applySorting(SearchSourceBuilder searchSourceBuilder, SortingDto sorting) {
    FieldSortBuilder defaultSorting = SortBuilders.fieldSort(ListViewTemplate.KEY).order(SortOrder.ASC);
    if (sorting == null) {
      //apply default sorting
      searchSourceBuilder.sort(defaultSorting);
    } else {
      String sortBy = sorting.getSortBy();
      if(sortBy.equals(ListViewTemplate.ID)) {
        sorting.setSortBy(ListViewTemplate.KEY);
      }
      searchSourceBuilder
        .sort(sorting.getSortBy(), SortOrder.fromString(sorting.getSortOrder()))
        .sort(defaultSorting);
    }
  }

  private QueryBuilder createRequestQuery(ListViewRequestDto request) {

    List<QueryBuilder> queries = new ArrayList<>();

    request.getQueries().stream()
      .forEach(query -> queries.add(createQueryFragment(query)));

    final TermQueryBuilder isWorkflowInstanceQuery = termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION);

    final QueryBuilder queryBuilder = joinWithAnd(isWorkflowInstanceQuery, joinWithOr(queries.toArray(new QueryBuilder[request.getQueries().size()])));

    return queryBuilder;
  }

  private QueryBuilder createQueryFragment(ListViewQueryDto query) {
    final QueryBuilder runningFinishedQuery = createRunningFinishedQuery(query);

    final QueryBuilder activityIdQuery = createActivityIdQuery(query);

    QueryBuilder idsQuery = null;
    if (query.getIds() != null && !query.getIds().isEmpty()) {
      idsQuery = createIdsQuery(query.getIds());
    }

    QueryBuilder errorMessageQuery = null;
    if (query.getErrorMessage() != null) {
      errorMessageQuery = createErrorMessageQuery(query.getErrorMessage());
    }

    QueryBuilder createDateQuery = null;
    if (query.getStartDateAfter() != null || query.getStartDateBefore() != null) {
      createDateQuery = createStartDateQuery(query);
    }

    QueryBuilder endDateQuery = null;
    if (query.getEndDateAfter() != null || query.getEndDateBefore() != null) {
      endDateQuery = createEndDateQuery(query);
    }

    QueryBuilder workflowIdQuery = null;
    if (query.getWorkflowIds() != null && !query.getWorkflowIds().isEmpty()) {
      workflowIdQuery = termsQuery(ListViewTemplate.WORKFLOW_ID,query.getWorkflowIds());
    }

    QueryBuilder bpmnProcessIdQuery = null;
    if (query.getBpmnProcessId() != null) {
      bpmnProcessIdQuery = createBpmnProcessIdQuery(query.getBpmnProcessId(), query.getWorkflowVersion());
    }

    QueryBuilder excludeIdsQuery = null;
    if (query.getExcludeIds() != null && !query.getExcludeIds().isEmpty()) {
      excludeIdsQuery = createExcludeIdsQuery(query.getExcludeIds());
    }

    QueryBuilder variablesQuery = null;
    if (query.getVariable() != null) {
      variablesQuery = createVariablesQuery(query.getVariable());
    }

    return joinWithAnd(runningFinishedQuery, activityIdQuery, idsQuery, errorMessageQuery, createDateQuery, endDateQuery, workflowIdQuery, bpmnProcessIdQuery, excludeIdsQuery, variablesQuery);
  }

  private QueryBuilder createBpmnProcessIdQuery(String bpmnProcessId, Integer workflowVersion) {
    final TermQueryBuilder bpmnProcessIdQ = termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId);
    TermQueryBuilder versionQ = null;
    if (workflowVersion != null) {
      versionQ = termQuery(ListViewTemplate.WORKFLOW_VERSION, workflowVersion);
    }
    return joinWithAnd(bpmnProcessIdQ, versionQ);
  }

  private QueryBuilder createVariablesQuery(VariablesQueryDto variablesQuery) {
    if (variablesQuery.getName() == null) {
      throw new InvalidRequestException("Variables query must provide not-null variable name.");
    }
    return hasChildQuery(VARIABLES_JOIN_RELATION,  joinWithAnd(termQuery(VAR_NAME, variablesQuery.getName()), termQuery(VAR_VALUE, variablesQuery.getValue())), None);
  }

  private QueryBuilder createExcludeIdsQuery(List<String> excludeIds) {
    return boolQuery().mustNot(termsQuery(ListViewTemplate.ID, excludeIds));
  }

  private QueryBuilder createEndDateQuery(ListViewQueryDto query) {
    final RangeQueryBuilder rangeQueryBuilder = rangeQuery(ListViewTemplate.END_DATE);
    if (query.getEndDateAfter() != null) {
      rangeQueryBuilder.gte(dateTimeFormatter.format(query.getEndDateAfter()));
    }
    if (query.getEndDateBefore() != null) {
      rangeQueryBuilder.lt(dateTimeFormatter.format(query.getEndDateBefore()));
    }
    rangeQueryBuilder.format(operateProperties.getElasticsearch().getDateFormat());
    return rangeQueryBuilder;
  }

  private QueryBuilder createStartDateQuery(ListViewQueryDto query) {
    final RangeQueryBuilder rangeQueryBuilder = rangeQuery(ListViewTemplate.START_DATE);
    if (query.getStartDateAfter() != null) {
      rangeQueryBuilder.gte(dateTimeFormatter.format(query.getStartDateAfter()));
    }
    if (query.getStartDateBefore() != null) {
      rangeQueryBuilder.lt(dateTimeFormatter.format(query.getStartDateBefore()));
    }
    rangeQueryBuilder.format(operateProperties.getElasticsearch().getDateFormat());

    return rangeQueryBuilder;
  }

  private QueryBuilder createErrorMessageQuery(String errorMessage) {
    return hasChildQuery(ACTIVITIES_JOIN_RELATION,  termQuery(ERROR_MSG, errorMessage), None);
  }

  private QueryBuilder createIdsQuery(List<String> ids) {
    return termsQuery(ListViewTemplate.ID, ids);
  }

  private Set<Long> findInstancesWithIncidents(List<Long> ids) {
    final TermQueryBuilder isWorkflowInstanceQ = termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION);
    final TermsQueryBuilder workflowInstanceIdsQ = termsQuery(ListViewTemplate.ID, ids);
    final HasChildQueryBuilder hasIncidentQ = hasChildQuery(ACTIVITIES_JOIN_RELATION, existsQuery(ListViewTemplate.INCIDENT_KEY), None);

    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    searchRequest.source(new SearchSourceBuilder()
      .query(constantScoreQuery(joinWithAnd(isWorkflowInstanceQ, workflowInstanceIdsQ, hasIncidentQ))));

    try {
      return CollectionUtil.toSafeSetOfLongs(ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient));
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining instances with incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private QueryBuilder createRunningFinishedQuery(ListViewQueryDto query) {

    boolean active = query.isActive();
    boolean incidents = query.isIncidents();
    boolean running = query.isRunning();

    boolean completed = query.isCompleted();
    boolean canceled = query.isCanceled();
    boolean finished = query.isFinished();

    if (!running && !finished) {
      //empty list should be returned
      return createMatchNoneQuery();
    }

    QueryBuilder runningQuery = null;

    if (running && (active || incidents)) {
      //running query
      runningQuery = boolQuery().mustNot(existsQuery(END_DATE));

      QueryBuilder activeQuery = createActiveQuery(query);
      QueryBuilder incidentsQuery = createIncidentsQuery(query);

      if (query.getActivityId() == null && query.isActive() && query.isIncidents()) {
         //we request all running instances
      } else {
        //some of the queries may be null
        runningQuery = joinWithAnd(runningQuery,
          joinWithOr(activeQuery, incidentsQuery));
      }
    }

    QueryBuilder finishedQuery = null;

    if (finished && (completed || canceled)) {

      //add finished query
      finishedQuery = existsQuery(END_DATE);

      QueryBuilder completedQuery = createCompletedQuery(query);
      QueryBuilder canceledQuery = createCanceledQuery(query);

      if (query.getActivityId() == null && query.isCompleted() && query.isCanceled()) {
        //we request all finished instances
      } else {
        finishedQuery = joinWithAnd(finishedQuery, joinWithOr(completedQuery, canceledQuery));
      }
    }

    final QueryBuilder workflowInstanceQuery = joinWithOr(runningQuery, finishedQuery);

    if (workflowInstanceQuery == null) {
      return createMatchNoneQuery();
    }

    return workflowInstanceQuery;

  }

  private QueryBuilder createActivityIdQuery(ListViewQueryDto query) {
    if (query.getActivityId() == null) {
      return null;
    }
    QueryBuilder activeActivityIdQuery = null;
    if (query.isActive()) {
      activeActivityIdQuery = createActivityIdQuery(query.getActivityId(), ActivityState.ACTIVE);
    }
    QueryBuilder incidentActivityIdQuery = null;
    if (query.isIncidents()) {
      incidentActivityIdQuery = createActivityIdIncidentQuery(query.getActivityId());
    }
    QueryBuilder completedActivityIdQuery = null;
    if (query.isCompleted()) {
      completedActivityIdQuery = createActivityIdQuery(query.getActivityId(), ActivityState.COMPLETED);
    }
    QueryBuilder canceledActivityIdQuery = null;
    if (query.isCanceled()) {
      canceledActivityIdQuery = createActivityIdQuery(query.getActivityId(), ActivityState.TERMINATED);
    }
    return joinWithOr(activeActivityIdQuery, incidentActivityIdQuery, completedActivityIdQuery, canceledActivityIdQuery);
  }

  private QueryBuilder createCanceledQuery(ListViewQueryDto query) {
    if (query.isCanceled()) {
      return termQuery(STATE, WorkflowInstanceState.CANCELED.toString());
    }
    return null;
  }

  private QueryBuilder createCompletedQuery(ListViewQueryDto query) {
    if (query.isCompleted()) {
      return termQuery(STATE, WorkflowInstanceState.COMPLETED.toString());
    }
    return null;
  }

  private QueryBuilder createIncidentsQuery(ListViewQueryDto query) {
    if (query.isIncidents()) {
      return hasChildQuery(ACTIVITIES_JOIN_RELATION, existsQuery(INCIDENT_KEY), None);
    }
    return null;
  }

  private QueryBuilder createActiveQuery(ListViewQueryDto query) {
    if (query.isActive()) {
      return boolQuery().mustNot(hasChildQuery(ACTIVITIES_JOIN_RELATION, existsQuery(INCIDENT_KEY), None));
    }
    return null;

  }

  private QueryBuilder createActivityIdQuery(String activityId, ActivityState state) {
    final QueryBuilder activitiesQuery = termQuery(ACTIVITY_STATE, state.name());
    final QueryBuilder activityIdQuery = termQuery(ACTIVITY_ID, activityId);
    QueryBuilder activityIsEndNodeQuery = null;
    if (state.equals(ActivityState.COMPLETED)) {
      activityIsEndNodeQuery = termQuery(ACTIVITY_TYPE, ActivityType.END_EVENT.name());
    }

    return hasChildQuery(ACTIVITIES_JOIN_RELATION,  joinWithAnd(activitiesQuery, activityIdQuery, activityIsEndNodeQuery), None);
  }

  private QueryBuilder createActivityIdIncidentQuery(String activityId) {
    final QueryBuilder activitiesQuery = termQuery(ACTIVITY_STATE, ActivityState.ACTIVE.name());
    final QueryBuilder activityIdQuery = termQuery(ACTIVITY_ID, activityId);
    final ExistsQueryBuilder incidentExists = existsQuery(ERROR_MSG);

    return hasChildQuery(ACTIVITIES_JOIN_RELATION,  joinWithAnd(activitiesQuery, activityIdQuery, incidentExists), None);
  }

  public Collection<ActivityStatisticsDto> getActivityStatistics(ListViewQueryDto query) {

    Map<String, ActivityStatisticsDto> statisticsMap = new HashMap<>();

    if (query.isActive()) {
      getStatisticsForActiveActivities(query, WorkflowInstanceState.ACTIVE, ActivityStatisticsDto::setActive, statisticsMap);
    }
    if (query.isCanceled()) {
      getStatisticsForActivities(query, ActivityState.TERMINATED, ActivityStatisticsDto::setCanceled, statisticsMap);
    }
    if (query.isIncidents()) {
      getStatisticsForIncidentsActivities(query, WorkflowInstanceState.ACTIVE, ActivityStatisticsDto::setIncidents, statisticsMap);
    }
    getStatisticsForFinishedActivities(query, ActivityStatisticsDto::setCompleted, statisticsMap);

    return statisticsMap.values();
  }

    /**
     * Attention! This method updates the map, passed as a parameter.
     */
  private void getStatisticsForActivities(ListViewQueryDto query, ActivityState activityState,
        StatisticsMapEntryUpdater entryUpdater,
        Map<String, ActivityStatisticsDto> statisticsMap) {

    final QueryBuilder q = constantScoreQuery(createQueryFragment(query));

    final String activities = "activities";
    final String activeActivitiesAggName = "active_activities";
    final String uniqueActivitiesAggName = "unique_activities";
    final String activityToWorkflowAggName = "activity_to_workflow";
    final ChildrenAggregationBuilder agg =
      children(activities, ListViewTemplate.ACTIVITIES_JOIN_RELATION)
        .subAggregation(filter(activeActivitiesAggName, termQuery(ACTIVITY_STATE, activityState.toString()))
          .subAggregation(terms(uniqueActivitiesAggName).field(ACTIVITY_ID)
              .size(ElasticsearchUtil.TERMS_AGG_SIZE)
             .subAggregation(parent(activityToWorkflowAggName, ACTIVITIES_JOIN_RELATION))    //we need this to count workflow instances, not the activity instances
            ));

    logger.debug("Activities statistics request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());

    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    searchRequest.source(new SearchSourceBuilder()
      .query(q)
      .size(0)
      .aggregation(agg));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      ((Terms)
        ((Filter)
          ((Children)(searchResponse.getAggregations().get(activities)))
            .getAggregations().get(activeActivitiesAggName))
          .getAggregations().get(uniqueActivitiesAggName))
        .getBuckets().stream().forEach(b -> {
        String activityId = b.getKeyAsString();
        final Parent aggregation = b.getAggregations().get(activityToWorkflowAggName);
        final long docCount = aggregation.getDocCount();  //number of workflow instances
        addToMap(statisticsMap, activityId, docCount, entryUpdater);
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining statistics for activities: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  /**
     * Attention! This method updates the map, passed as a parameter.
     */
  private void getStatisticsForActiveActivities(ListViewQueryDto query, WorkflowInstanceState workflowInstanceState,
        StatisticsMapEntryUpdater entryUpdater,
        Map<String, ActivityStatisticsDto> statisticsMap) {

    final QueryBuilder q = constantScoreQuery(createQueryFragment(query));

    final String activities = "activities";
    final String activeActivitiesAggName = "active_activities";
    final String uniqueActivitiesAggName = "unique_activities";
    final String activityToWorkflowAggName = "activity_to_workflow";
    final ChildrenAggregationBuilder agg =
      children(activities, ListViewTemplate.ACTIVITIES_JOIN_RELATION)
        .subAggregation(filter(activeActivitiesAggName, boolQuery().mustNot(existsQuery(INCIDENT_KEY)).must(termQuery(ACTIVITY_STATE, ActivityState.ACTIVE.toString())))
          .subAggregation(terms(uniqueActivitiesAggName).field(ACTIVITY_ID)
             .size(ElasticsearchUtil.TERMS_AGG_SIZE)
             .subAggregation(parent(activityToWorkflowAggName, ACTIVITIES_JOIN_RELATION))    //we need this to count workflow instances, not the activity instances
            ));

    logger.debug("Activities statistics request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());

    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    searchRequest.source(new SearchSourceBuilder()
      .query(q)
      .size(0)
      .aggregation(agg));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      ((Terms)
        ((Filter)
          ((Children)(searchResponse.getAggregations().get(activities)))
            .getAggregations().get(activeActivitiesAggName))
        .getAggregations().get(uniqueActivitiesAggName))
      .getBuckets().stream().forEach(b -> {
        String activityId = b.getKeyAsString();
        final Parent aggregation = b.getAggregations().get(activityToWorkflowAggName);
        final long docCount = aggregation.getDocCount();  //number of workflow instances
        addToMap(statisticsMap, activityId, docCount, entryUpdater);
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining statistics for activities: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  /**
     * Attention! This method updates the map, passed as a parameter.
     */
  private void getStatisticsForIncidentsActivities(ListViewQueryDto query, WorkflowInstanceState workflowInstanceState,
        StatisticsMapEntryUpdater entryUpdater,
        Map<String, ActivityStatisticsDto> statisticsMap) {

    final QueryBuilder q = constantScoreQuery(createQueryFragment(query));

    final String activities = "activities";
    final String incidentActivitiesAggName = "incident_activities";
    final String uniqueActivitiesAggName = "unique_activities";
    final String activityToWorkflowAggName = "activity_to_workflow";
    final ChildrenAggregationBuilder agg =
      children(activities, ListViewTemplate.ACTIVITIES_JOIN_RELATION)
        .subAggregation(filter(incidentActivitiesAggName, existsQuery(INCIDENT_KEY))
          .subAggregation(terms(uniqueActivitiesAggName).field(ACTIVITY_ID)
              .size(ElasticsearchUtil.TERMS_AGG_SIZE)
             .subAggregation(parent(activityToWorkflowAggName, ACTIVITIES_JOIN_RELATION))    //we need this to count workflow instances, not the activity instances
            ));

    logger.debug("Activities statistics request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());

    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    searchRequest.source(new SearchSourceBuilder()
      .query(q)
      .size(0)
      .aggregation(agg));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      ((Terms)
        ((Filter)
          ((Children)(searchResponse.getAggregations().get(activities)))
            .getAggregations().get(incidentActivitiesAggName))
        .getAggregations().get(uniqueActivitiesAggName))
      .getBuckets().stream().forEach(b -> {
        String activityId = b.getKeyAsString();
        final Parent aggregation = b.getAggregations().get(activityToWorkflowAggName);
        final long docCount = aggregation.getDocCount();  //number of workflow instances
        addToMap(statisticsMap, activityId, docCount, entryUpdater);
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining statistics for activities: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  /**
   * Attention! This method updates the map, passed as a parameter.
   */
  private void getStatisticsForFinishedActivities(ListViewQueryDto query, StatisticsMapEntryUpdater entryUpdater, Map<String, ActivityStatisticsDto> statisticsMap) {

    final QueryBuilder q = constantScoreQuery(createQueryFragment(query));

    final String activities = "activities";
    final String activeActivitiesAggName = "active_activities";
    final String uniqueActivitiesAggName = "unique_activities";
    final String activityToWorkflowAggName = "activity_to_workflow";
    final QueryBuilder completedEndEventsQ = joinWithAnd(termQuery(ACTIVITY_TYPE, ActivityType.END_EVENT.toString()), termQuery(ACTIVITY_STATE, ActivityState.COMPLETED.toString()));
    final ChildrenAggregationBuilder agg =
      children(activities, ListViewTemplate.ACTIVITIES_JOIN_RELATION)
        .subAggregation(filter(activeActivitiesAggName, completedEndEventsQ)
          .subAggregation(terms(uniqueActivitiesAggName).field(ACTIVITY_ID)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(parent(activityToWorkflowAggName, ACTIVITIES_JOIN_RELATION))     //we need this to count workflow instances, not the activity instances
          ));
    logger.debug("Finished activities statistics request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());

    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    searchRequest.source(new SearchSourceBuilder()
      .query(q)
      .size(0)
      .aggregation(agg));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      ((Terms)
        ((Filter)
          ((Children)(searchResponse.getAggregations().get(activities)))
            .getAggregations().get(activeActivitiesAggName))
          .getAggregations().get(uniqueActivitiesAggName))
        .getBuckets().stream().forEach(b -> {
        String activityId = b.getKeyAsString();
        final Parent aggregation = b.getAggregations().get(activityToWorkflowAggName);
        final long docCount = aggregation.getDocCount();  //number of workflow instances
        addToMap(statisticsMap, activityId, docCount, entryUpdater);
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining statistics for activities: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  private void addToMap(Map<String, ActivityStatisticsDto> statisticsMap, String activityId, Long docCount, StatisticsMapEntryUpdater entryUpdater) {
    if (statisticsMap.get(activityId) == null) {
      statisticsMap.put(activityId, new ActivityStatisticsDto(activityId));
    }
    entryUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
  }

  @FunctionalInterface
  interface StatisticsMapEntryUpdater {
    void updateMapEntry(ActivityStatisticsDto statistics, Long value);
  }

}
