package org.camunda.operate.es.reader;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.ActivityStatisticsDto;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.dto.VariablesQueryDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.rest.dto.WorkflowInstanceResponseDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.apache.lucene.search.join.ScoreMode.None;
import static org.camunda.operate.entities.IncidentState.ACTIVE;
import static org.camunda.operate.entities.OperationState.LOCKED;
import static org.camunda.operate.entities.OperationState.SCHEDULED;
import static org.camunda.operate.es.types.WorkflowInstanceType.ACTIVITIES;
import static org.camunda.operate.es.types.WorkflowInstanceType.ACTIVITY_ID;
import static org.camunda.operate.es.types.WorkflowInstanceType.BOOLEAN_VARIABLES;
import static org.camunda.operate.es.types.WorkflowInstanceType.DOUBLE_VARIABLES;
import static org.camunda.operate.es.types.WorkflowInstanceType.END_DATE;
import static org.camunda.operate.es.types.WorkflowInstanceType.ERROR_MSG;
import static org.camunda.operate.es.types.WorkflowInstanceType.INCIDENTS;
import static org.camunda.operate.es.types.WorkflowInstanceType.LOCK_EXPIRATION_TIME;
import static org.camunda.operate.es.types.WorkflowInstanceType.LONG_VARIABLES;
import static org.camunda.operate.es.types.WorkflowInstanceType.OPERATIONS;
import static org.camunda.operate.es.types.WorkflowInstanceType.STATE;
import static org.camunda.operate.es.types.WorkflowInstanceType.STRING_VARIABLES;
import static org.camunda.operate.es.types.WorkflowInstanceType.VARIABLE_NAME;
import static org.camunda.operate.es.types.WorkflowInstanceType.VARIABLE_VALUE;
import static org.camunda.operate.util.ElasticsearchUtil.createMatchNoneQuery;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class WorkflowInstanceReader {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceReader.class);

  private static final String ACTIVE_INCIDENT = ACTIVE.toString();
  private static final String INCIDENT_STATE_TERM = String.format("%s.%s", INCIDENTS, STATE);
  private static final String INCIDENT_ERRORMSG_TERM = String.format("%s.%s", INCIDENTS, ERROR_MSG);
  private static final String ACTIVITY_STATE_TERM = String.format("%s.%s", ACTIVITIES, STATE);
  private static final String ACTIVITY_TYPE_TERM = String.format("%s.%s", ACTIVITIES, WorkflowInstanceType.TYPE);
  private static final String ACTIVITY_ACTIVITYID_TERM = String.format("%s.%s", ACTIVITIES, ACTIVITY_ID);
  private static final String OPERATION_STATE_TERM = String.format("%s.%s", OPERATIONS, STATE);
  private static final String SCHEDULED_OPERATION = SCHEDULED.toString();
  private static final String LOCKED_OPERATION = LOCKED.toString();
  private static final String LOCK_EXPIRATION_TIME_TERM = String.format("%s.%s", OPERATIONS, LOCK_EXPIRATION_TIME);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowInstanceType workflowInstanceType;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  /**
   *
   * @param workflowId
   * @return
   */
  public List<String> queryWorkflowInstancesWithEmptyWorkflowVersion(long workflowId) {

    final QueryBuilder queryBuilder =
      joinWithAnd(
        termQuery(WorkflowInstanceType.WORKFLOW_ID, workflowId),
        boolQuery()
          .mustNot(existsQuery(WorkflowInstanceType.WORKFLOW_VERSION)));
    //workflow name can be null, as some workflows does not have name


    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowInstanceType.getAlias())
        .setQuery(constantScoreQuery(queryBuilder))
        .setFetchSource(false);

    return scrollIds(searchRequestBuilder);
  }

  /**
   * Queries workflow instances by different criteria (with pagination).
   * @param workflowInstanceRequest
   * @param firstResult
   * @param maxResults
   * @return
   */
  public WorkflowInstanceResponseDto queryWorkflowInstances(WorkflowInstanceRequestDto workflowInstanceRequest, Integer firstResult, Integer maxResults) {
    SearchRequestBuilder searchRequest = createSearchRequest(workflowInstanceRequest)
      .setFetchSource(null, new String[]{WorkflowInstanceType.ACTIVITIES, WorkflowInstanceType.SEQUENCE_FLOWS});

    if (firstResult != null && maxResults != null) {
      return paginate(searchRequest, firstResult, maxResults);
    }
    else {
      return getAllResults(searchRequest);
    }
  }

  public SearchRequestBuilder createSearchRequest(WorkflowInstanceRequestDto request) {

    final QueryBuilder query = createRequestQuery(request);

    ConstantScoreQueryBuilder constantScoreQuery = constantScoreQuery(query);

    logger.debug("Workflow instance search request: \n{}", constantScoreQuery.toString());

    final SearchRequestBuilder searchRequestBuilder = esClient.prepareSearch(workflowInstanceType.getAlias());
    applySorting(searchRequestBuilder, request.getSorting());
    return searchRequestBuilder.setQuery(constantScoreQuery);
  }

  private void applySorting(SearchRequestBuilder searchRequestBuilder, SortingDto sorting) {
    if (sorting == null) {
      //apply default sorting
      searchRequestBuilder.addSort(WorkflowInstanceType.ID, SortOrder.ASC);
    } else {
      searchRequestBuilder.addSort(sorting.getSortBy(), SortOrder.fromString(sorting.getSortOrder()));
    }
  }

  private QueryBuilder createRequestQuery(WorkflowInstanceRequestDto request) {

    List<QueryBuilder> queries = new ArrayList<>();

    request.getQueries().stream()
      .forEach(query -> queries.add(createQueryFragment(query)));

    final QueryBuilder queryBuilder = joinWithOr(queries.toArray(new QueryBuilder[request.getQueries().size()]));

    if (queryBuilder == null) {
      return createMatchNoneQuery();
    }

    return queryBuilder;
  }

  private QueryBuilder createQueryFragment(WorkflowInstanceQueryDto query) {
    final QueryBuilder runningFinishedQuery = createRunningFinishedQuery(query);

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
      workflowIdQuery = createWorkflowIdsQuery(query.getWorkflowIds());
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
    if (query.getVariablesQuery() != null) {
      variablesQuery = createVariablesQuery(query.getVariablesQuery());
    }

    return joinWithAnd(runningFinishedQuery, idsQuery, errorMessageQuery, createDateQuery, endDateQuery, workflowIdQuery, bpmnProcessIdQuery, excludeIdsQuery, variablesQuery);
  }

  private QueryBuilder createBpmnProcessIdQuery(String bpmnProcessId, Integer workflowVersion) {
    final TermQueryBuilder bpmnProcessIdQ = termQuery(WorkflowInstanceType.BPMN_PROCESS_ID, bpmnProcessId);
    TermQueryBuilder versionQ = null;
    if (workflowVersion != null) {
      versionQ = termQuery(WorkflowInstanceType.WORKFLOW_VERSION, workflowVersion);
    }
    return joinWithAnd(bpmnProcessIdQ, versionQ);
  }

  private QueryBuilder createVariablesQuery(VariablesQueryDto variablesQuery) {
    if (variablesQuery.getName() == null) {
      throw new InvalidRequestException("Variables query must provide not-null variable name.");
    }
    if (variablesQuery.getValue() instanceof Long || variablesQuery.getValue() instanceof Integer) {
      return createVariableQuery(LONG_VARIABLES, variablesQuery);
    } else if (variablesQuery.getValue() instanceof String || variablesQuery.getValue() == null) {
      return createVariableQuery(STRING_VARIABLES, variablesQuery);
    } else if (variablesQuery.getValue() instanceof Double) {
      return createVariableQuery(DOUBLE_VARIABLES, variablesQuery);
    } else if (variablesQuery.getValue() instanceof Boolean) {
      return createVariableQuery(BOOLEAN_VARIABLES, variablesQuery);
    } else {
      logger.warn("Unable to search for variable {} with given value type: {}", variablesQuery.getName(), variablesQuery.getValue().getClass().getName());
      return null;
    }
  }

  private QueryBuilder createVariableQuery(String nestedCollectionName, VariablesQueryDto variablesQuery) {
    return nestedQuery(nestedCollectionName,
      joinWithAnd(
        termQuery(String.format("%s.%s", nestedCollectionName, VARIABLE_NAME), variablesQuery.getName()),
        termQuery(String.format("%s.%s", nestedCollectionName, VARIABLE_VALUE), variablesQuery.getValue() == null ? WorkflowInstanceType.NULL_VALUE : variablesQuery.getValue())
      ), None);
  }

  private QueryBuilder createExcludeIdsQuery(List<String> excludeIds) {
    return boolQuery().mustNot(termsQuery(WorkflowInstanceType.ID, excludeIds));
  }

  private QueryBuilder createWorkflowIdsQuery(List<String> workflowIds) {
    return termsQuery(WorkflowInstanceType.WORKFLOW_ID, workflowIds);
  }

  private QueryBuilder createEndDateQuery(WorkflowInstanceQueryDto query) {
    final RangeQueryBuilder rangeQueryBuilder = rangeQuery(WorkflowInstanceType.END_DATE);
    if (query.getEndDateAfter() != null) {
      rangeQueryBuilder.gte(dateTimeFormatter.format(query.getEndDateAfter()));
    }
    if (query.getEndDateBefore() != null) {
      rangeQueryBuilder.lt(dateTimeFormatter.format(query.getEndDateBefore()));
    }
    rangeQueryBuilder.format(operateProperties.getElasticsearch().getDateFormat());
    return rangeQueryBuilder;
  }

  private QueryBuilder createStartDateQuery(WorkflowInstanceQueryDto query) {
    final RangeQueryBuilder rangeQueryBuilder = rangeQuery(WorkflowInstanceType.START_DATE);
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
    final QueryBuilder activeIncidentsQuery = termQuery(INCIDENT_STATE_TERM, ACTIVE_INCIDENT);
    final QueryBuilder errorMessageQuery = termQuery(INCIDENT_ERRORMSG_TERM, errorMessage);
    return nestedQuery(INCIDENTS, joinWithAnd(activeIncidentsQuery, errorMessageQuery), None);
  }

  private QueryBuilder createIdsQuery(List<String> ids) {
    return termsQuery(WorkflowInstanceType.ID, ids);
  }

  protected WorkflowInstanceResponseDto paginate(SearchRequestBuilder builder, int firstResult, int maxResults) {
    SearchResponse response = builder
      .setFrom(firstResult)
      .setSize(maxResults)
      .get();

    final List<WorkflowInstanceEntity> workflowInstanceEntities = ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, WorkflowInstanceEntity.class);
    WorkflowInstanceResponseDto responseDto = new WorkflowInstanceResponseDto();
    responseDto.setWorkflowInstances(WorkflowInstanceDto.createFrom(workflowInstanceEntities));
    responseDto.setTotalCount(response.getHits().getTotalHits());
    return responseDto;
  }

  protected WorkflowInstanceResponseDto getAllResults(SearchRequestBuilder builder) {
    List<WorkflowInstanceEntity> result = scroll(builder);

    WorkflowInstanceResponseDto responseDto = new WorkflowInstanceResponseDto();
    responseDto.setWorkflowInstances(WorkflowInstanceDto.createFrom(result));
    responseDto.setTotalCount(result.size());
    return responseDto;
  }

  private List<WorkflowInstanceEntity> scroll(SearchRequestBuilder builder) {
    List<WorkflowInstanceEntity> result = new ArrayList<>();
    TimeValue keepAlive = new TimeValue(5000);
    SearchResponse response = builder
      .setScroll(keepAlive)
      .get();
    do {
      SearchHits hits = response.getHits();
      String scrollId = response.getScrollId();

      result.addAll(ElasticsearchUtil.mapSearchHits(hits.getHits(), objectMapper, WorkflowInstanceEntity.class));

      response = esClient
          .prepareSearchScroll(scrollId)
          .setScroll(keepAlive)
          .get();

    } while (response.getHits().getHits().length != 0);
    return result;
  }

  private List<String> scrollIds(SearchRequestBuilder builder) {
    List<String> result = new ArrayList<>();
    TimeValue keepAlive = new TimeValue(5000);
    SearchResponse response = builder
      .setScroll(keepAlive)
      .get();
    do {
      SearchHits hits = response.getHits();
      String scrollId = response.getScrollId();

      result.addAll(Arrays.stream(hits.getHits()).collect(ArrayList::new, (list, hit) -> list.add(hit.getId()), (list1, list2) -> list1.addAll(list2)));

      response = esClient
          .prepareSearchScroll(scrollId)
          .setScroll(keepAlive)
          .get();

    } while (response.getHits().getHits().length != 0);
    return result;
  }

  /**
   * Searches for workflow instance by id.
   * @param workflowInstanceId
   * @return
   */
  public WorkflowInstanceEntity getWorkflowInstanceById(String workflowInstanceId) {
    final TermQueryBuilder q = termQuery(ElasticsearchUtil.ES_ID_FIELD_NAME, workflowInstanceId);

    final SearchResponse response = esClient.prepareSearch(workflowInstanceType.getAlias())
      .setQuery(q)
      .get();

    if (response.getHits().totalHits == 1) {
      return ElasticsearchUtil.fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, WorkflowInstanceEntity.class);
    } else if (response.getHits().totalHits > 1) {
      throw new NotFoundException(String.format("Could not find unique workflow instance with id '%s'.", workflowInstanceId));
    } else {
      throw new NotFoundException(String.format("Could not find workflow instance with id '%s'.", workflowInstanceId));
    }
  }

  private QueryBuilder createRunningFinishedQuery(WorkflowInstanceQueryDto query) {

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
      QueryBuilder activeActivityIdQuery = createActivityIdQuery(query.getActivityId(), ActivityState.ACTIVE, query.isActive());
      QueryBuilder incidentsQuery = createIncidentsQuery(query);
      QueryBuilder incidentActivityIdQuery = createActivityIdQuery(query.getActivityId(), ActivityState.INCIDENT, query.isIncidents());

      if (query.getActivityId() == null && query.isActive() && query.isIncidents()) {
         //we request all running instances
      } else {
        //some of the queries may be null
        runningQuery = joinWithAnd(runningQuery,
          joinWithOr(joinWithAnd(activeQuery, activeActivityIdQuery), joinWithAnd(incidentsQuery, incidentActivityIdQuery)));
      }
    }

    QueryBuilder finishedQuery = null;

    if (finished && (completed || canceled)) {

      //add finished query
      finishedQuery = existsQuery(END_DATE);

      QueryBuilder completedQuery = createCompletedQuery(query);
      QueryBuilder completedActivityIdQuery = createActivityIdQuery(query.getActivityId(), ActivityState.COMPLETED, query.isCompleted());
      QueryBuilder canceledQuery = createCanceledQuery(query);
      QueryBuilder canceledActivityIdQuery = createActivityIdQuery(query.getActivityId(), ActivityState.TERMINATED, query.isCanceled());

      if (query.getActivityId() == null && query.isCompleted() && query.isCanceled()) {
        //we request all finished instances
      } else {
        finishedQuery = joinWithAnd(finishedQuery, joinWithOr(joinWithAnd(completedQuery, completedActivityIdQuery), joinWithAnd(canceledQuery, canceledActivityIdQuery)));
      }
    }

    final QueryBuilder workflowInstanceQuery = joinWithOr(runningQuery, finishedQuery);

    if (workflowInstanceQuery == null) {
      return createMatchNoneQuery();
    }

    return workflowInstanceQuery;

  }

  private QueryBuilder createCanceledQuery(WorkflowInstanceQueryDto query) {
    if (query.isCanceled()) {
      return termQuery(STATE, WorkflowInstanceState.CANCELED.toString());
    }
    return null;
  }

  private QueryBuilder createCompletedQuery(WorkflowInstanceQueryDto query) {
    if (query.isCompleted()) {
      return termQuery(STATE, WorkflowInstanceState.COMPLETED.toString());
    }
    return null;
  }

  private QueryBuilder createIncidentsQuery(WorkflowInstanceQueryDto query) {
    if (query.isIncidents()) {
      return nestedQuery(INCIDENTS, termQuery(INCIDENT_STATE_TERM, ACTIVE_INCIDENT), None);
    }
    return null;
  }

  private QueryBuilder createActiveQuery(WorkflowInstanceQueryDto query) {
    if (query.isActive()) {
      return boolQuery().mustNot(nestedQuery(INCIDENTS, termQuery(INCIDENT_STATE_TERM, ACTIVE_INCIDENT), None));
    }
    return null;

  }

  private QueryBuilder createActivityIdQuery(String activityId, ActivityState state, boolean createQuery) {
    if (activityId == null || createQuery == false) {
      return null;
    }
    final QueryBuilder activitiesQuery = termQuery(ACTIVITY_STATE_TERM, state.name());
    final QueryBuilder activityIdQuery = termQuery(ACTIVITY_ACTIVITYID_TERM, activityId);
    QueryBuilder activityIsEndNodeQuery = null;
    if (state.equals(ActivityState.COMPLETED)) {
      activityIsEndNodeQuery = termQuery(ACTIVITY_TYPE_TERM, ActivityType.END_EVENT.name());
    }
    return nestedQuery(ACTIVITIES, joinWithAnd(activitiesQuery, activityIdQuery, activityIsEndNodeQuery), None);
  }

  /**
   * Request workflow instances, that have scheduled operations or locked but with expired locks.
   * @param batchSize
   * @return
   */
  public List<WorkflowInstanceEntity> acquireOperations(int batchSize) {
    final TermQueryBuilder scheduledOperationsQuery = termQuery(OPERATION_STATE_TERM, SCHEDULED_OPERATION);
    final TermQueryBuilder lockedOperationsQuery = termQuery(OPERATION_STATE_TERM, LOCKED_OPERATION);
    final RangeQueryBuilder lockExpirationTimeQuery = rangeQuery(LOCK_EXPIRATION_TIME_TERM);
    lockExpirationTimeQuery.lte(dateTimeFormatter.format(OffsetDateTime.now()));

    final QueryBuilder operationsQuery = joinWithOr(scheduledOperationsQuery, joinWithAnd(lockedOperationsQuery, lockExpirationTimeQuery));
    final NestedQueryBuilder nestedQuery = nestedQuery(OPERATIONS, operationsQuery, None);

    ConstantScoreQueryBuilder constantScoreQuery = constantScoreQuery(nestedQuery);

    final SearchRequestBuilder searchRequestBuilder = esClient.prepareSearch(workflowInstanceType.getAlias());
    searchRequestBuilder.setQuery(constantScoreQuery);

    SearchResponse response = searchRequestBuilder
      .setFrom(0)
      .setSize(batchSize)
      .get();

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, WorkflowInstanceEntity.class);
  }

  public Collection<ActivityStatisticsDto> getActivityStatistics(WorkflowInstanceQueryDto query) {

    Map<String, ActivityStatisticsDto> statisticsMap = new HashMap<>();

    if (query.isActive()) {
      getStatisticsForActivities(query, WorkflowInstanceState.ACTIVE, ActivityState.ACTIVE, ActivityStatisticsDto::setActive, statisticsMap);
    }
    if (query.isCanceled()) {
      getStatisticsForActivities(query, WorkflowInstanceState.CANCELED, ActivityState.TERMINATED, ActivityStatisticsDto::setCanceled, statisticsMap);
    }
    if (query.isIncidents()) {
      getStatisticsForActivities(query, WorkflowInstanceState.ACTIVE, ActivityState.INCIDENT, ActivityStatisticsDto::setIncidents, statisticsMap);
    }
    getStatisticsForFinishedActivities(query, ActivityStatisticsDto::setCompleted, statisticsMap);

    return statisticsMap.values();
  }

    /**
     * Attention! This method updates the map, passed as a parameter.
     */
  private void getStatisticsForActivities(WorkflowInstanceQueryDto query, WorkflowInstanceState workflowInstanceState, ActivityState activityState,
        StatisticsMapEntryUpdater entryUpdater,
        Map<String, ActivityStatisticsDto> statisticsMap) {

    final QueryBuilder q = constantScoreQuery(createQueryFragment(query));

    final String activitiesAggName = "activities";
    final String activeActivitiesAggName = "active_activities";
    final String uniqueActivitiesAggName = "unique_activities";
    final String activityToWorkflowAggName = "activity_to_workflow";
    final NestedAggregationBuilder agg =
      nested(activitiesAggName, WorkflowInstanceType.ACTIVITIES).subAggregation(
        filter(activeActivitiesAggName, termQuery(ACTIVITY_STATE_TERM, activityState.toString()))
          .subAggregation(terms(uniqueActivitiesAggName).field(ACTIVITY_ACTIVITYID_TERM)
             .size(100) //TODO
             .subAggregation(reverseNested(activityToWorkflowAggName))    //we need this to count workflow instances, not the activity instances
            )
            );

    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowInstanceType.getAlias())
        .setSize(0)
        .setQuery(q)
        .addAggregation(agg);

    logger.debug("Active activities statistics request: \n{}\n and aggregation: \n{}", q.toString(), agg.toString());

    final SearchResponse searchResponse = searchRequestBuilder.get();

    ((Terms)
      ((Filter)
        ((Nested)searchResponse.getAggregations().get(activitiesAggName))
        .getAggregations().get(activeActivitiesAggName))
      .getAggregations().get(uniqueActivitiesAggName))
    .getBuckets().stream().forEach(b -> {
      String activityId = b.getKeyAsString();
      final ReverseNested aggregation = b.getAggregations().get(activityToWorkflowAggName);
      final long docCount = aggregation.getDocCount();  //number of workflow instances
      addToMap(statisticsMap, activityId, docCount, entryUpdater);
    });

  }

  /**
   * Attention! This method updates the map, passed as a parameter.
   */
  private void getStatisticsForFinishedActivities(WorkflowInstanceQueryDto query, StatisticsMapEntryUpdater entryUpdater, Map<String, ActivityStatisticsDto> statisticsMap) {

    final QueryBuilder q = constantScoreQuery(createQueryFragment(query));

    final String activitiesAggName = "activities";
    final String activeActivitiesAggName = "active_activities";
    final String uniqueActivitiesAggName = "unique_activities";
    final String activityToWorkflowAggName = "activity_to_workflow";
    final QueryBuilder completedEndEventsQ = joinWithAnd(termQuery(ACTIVITY_TYPE_TERM, ActivityType.END_EVENT.toString()), termQuery(ACTIVITY_STATE_TERM, ActivityState.COMPLETED.toString()));
    final NestedAggregationBuilder agg =
      nested(activitiesAggName, WorkflowInstanceType.ACTIVITIES).subAggregation(
        filter(activeActivitiesAggName, completedEndEventsQ)
          .subAggregation(terms(uniqueActivitiesAggName).field(ACTIVITY_ACTIVITYID_TERM)
            .size(100) //TODO
            .subAggregation(reverseNested(activityToWorkflowAggName))    //we need this to count workflow instances, not the activity instances
          )
      );

    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowInstanceType.getAlias())
        .setSize(0)
        .setQuery(q)
        .addAggregation(agg);

    logger.debug("Active activities statistics request: \n{}\n and aggregation: \n{}", query.toString(), agg.toString());

    final SearchResponse searchResponse = searchRequestBuilder.get();

    ((Terms)
      ((Filter)
        ((Nested)searchResponse.getAggregations().get(activitiesAggName))
          .getAggregations().get(activeActivitiesAggName))
        .getAggregations().get(uniqueActivitiesAggName))
      .getBuckets().stream().forEach(b -> {
      String activityId = b.getKeyAsString();
      final ReverseNested aggregation = b.getAggregations().get(activityToWorkflowAggName);
      final long docCount = aggregation.getDocCount();  //number of workflow instances
      addToMap(statisticsMap, activityId, docCount, entryUpdater);
    });

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
