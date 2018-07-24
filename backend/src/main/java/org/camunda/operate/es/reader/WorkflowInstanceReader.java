package org.camunda.operate.es.reader;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.rest.dto.WorkflowInstanceResponseDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.rest.exception.NotFoundException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.apache.lucene.search.join.ScoreMode.None;
import static org.camunda.operate.entities.IncidentState.ACTIVE;
import static org.camunda.operate.es.types.WorkflowInstanceType.ACTIVITIES;
import static org.camunda.operate.es.types.WorkflowInstanceType.ACTIVITY_ID;
import static org.camunda.operate.es.types.WorkflowInstanceType.END_DATE;
import static org.camunda.operate.es.types.WorkflowInstanceType.ERROR_MSG;
import static org.camunda.operate.es.types.WorkflowInstanceType.INCIDENTS;
import static org.camunda.operate.es.types.WorkflowInstanceType.STATE;
import static org.camunda.operate.util.ElasticsearchUtil.createMatchNoneQuery;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class WorkflowInstanceReader {

  private Logger logger = LoggerFactory.getLogger(WorkflowInstanceReader.class);

  private static final String ACTIVE_INCIDENT = ACTIVE.toString();
  private static final String INCIDENT_STATE_TERM = String.format("%s.%s", INCIDENTS, STATE);
  private static final String INCIDENT_ERRORMSG_TERM = String.format("%s.%s", INCIDENTS, ERROR_MSG);
  private static final String ACTIVE_ACTIVITY = ActivityState.ACTIVE.toString();
  private static final String ACTIVITY_STATE_TERM = String.format("%s.%s", ACTIVITIES, STATE);
  private static final String ACTIVITY_ACTIVITYID_TERM = String.format("%s.%s", ACTIVITIES, ACTIVITY_ID);

  @Autowired
  private TransportClient esClient;

  @Autowired
  @Qualifier("esObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowInstanceType workflowInstanceType;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  /**
   * Queries workflow instances by different criteria (with pagination).
   * @param workflowInstanceRequest
   * @param firstResult
   * @param maxResults
   * @return
   */
  public WorkflowInstanceResponseDto queryWorkflowInstances(WorkflowInstanceRequestDto workflowInstanceRequest, Integer firstResult, Integer maxResults) {
    SearchRequestBuilder searchRequest = createSearchRequest(workflowInstanceRequest)
      .setFetchSource(null, WorkflowInstanceType.ACTIVITIES);

    if (firstResult != null && maxResults != null) {
      return paginate(searchRequest, firstResult, maxResults);
    }
    else {
      return scroll(searchRequest);
    }

  }

  protected SearchRequestBuilder createSearchRequest(WorkflowInstanceRequestDto request) {

    final QueryBuilder query = createRequestQuery(request);

    ConstantScoreQueryBuilder constantScoreQuery = QueryBuilders.constantScoreQuery(query);

    logger.debug("Workflow instance search request: \n{}", constantScoreQuery.toString());

    final SearchRequestBuilder searchRequestBuilder = esClient.prepareSearch(workflowInstanceType.getType());
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

    QueryBuilder activityIdQuery = null;
    if (query.getActivityId() != null) {
      activityIdQuery = createActivityIdQuery(query.getActivityId());
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

    QueryBuilder excludeIdsQuery = null;
    if (query.getExcludeIds() != null && !query.getExcludeIds().isEmpty()) {
      excludeIdsQuery = createExcludeIdsQuery(query.getExcludeIds());
    }

    return joinWithAnd(runningFinishedQuery, idsQuery, errorMessageQuery, activityIdQuery, createDateQuery, endDateQuery, workflowIdQuery, excludeIdsQuery);
  }

  private QueryBuilder createExcludeIdsQuery(List<String> excludeIds) {
    return boolQuery().mustNot(termsQuery(WorkflowInstanceType.ID, excludeIds));
  }

  private QueryBuilder createWorkflowIdsQuery(List<String> workflowId) {
    return termsQuery(WorkflowInstanceType.WORKFLOW_ID, workflowId);
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

  private QueryBuilder createActivityIdQuery(String activityId) {
    final QueryBuilder activeActivitiesQuery = termQuery(ACTIVITY_STATE_TERM, ACTIVE_ACTIVITY);
    final QueryBuilder activityIdQuery = termQuery(ACTIVITY_ACTIVITYID_TERM, activityId);
    return nestedQuery(ACTIVITIES, joinWithAnd(activeActivitiesQuery, activityIdQuery), None);
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

    final List<WorkflowInstanceEntity> workflowInstanceEntities = mapSearchHits(response.getHits().getHits());
    WorkflowInstanceResponseDto responseDto = new WorkflowInstanceResponseDto();
    responseDto.setWorkflowInstances(WorkflowInstanceDto.createFrom(workflowInstanceEntities));
    responseDto.setTotalCount(response.getHits().getTotalHits());
    return responseDto;
  }

  protected WorkflowInstanceResponseDto scroll(SearchRequestBuilder builder) {
    TimeValue keepAlive = new TimeValue(60000);

    SearchResponse response = builder
      .setScroll(keepAlive)
      .get();

    List<WorkflowInstanceEntity> result = new ArrayList<>();

    do {

      SearchHits hits = response.getHits();
      String scrollId = response.getScrollId();

      result.addAll(mapSearchHits(hits.getHits()));

      response = esClient
          .prepareSearchScroll(scrollId)
          .setScroll(keepAlive)
          .get();

    } while (response.getHits().getHits().length != 0);

    WorkflowInstanceResponseDto responseDto = new WorkflowInstanceResponseDto();
    responseDto.setWorkflowInstances(WorkflowInstanceDto.createFrom(result));
    responseDto.setTotalCount(response.getHits().getTotalHits());
    return responseDto;
  }

  protected List<WorkflowInstanceEntity> mapSearchHits(SearchHit[] searchHits) {
    List<WorkflowInstanceEntity> result = new ArrayList<>();
    for (SearchHit searchHit : searchHits) {
      String searchHitAsString = searchHit.getSourceAsString();
      result.add(fromSearchHit(searchHitAsString));
    }
    return result;
  }

  /**
   * Searches for workflow instance by id.
   * @param workflowInstanceId
   * @return
   */
  public WorkflowInstanceEntity getWorkflowInstanceById(String workflowInstanceId) {
    final GetResponse response = esClient.prepareGet(workflowInstanceType.getType(), workflowInstanceType.getType(), workflowInstanceId).get();

    if (response.isExists()) {
      return fromSearchHit(response.getSourceAsString());
    }
    else {
      throw new NotFoundException(String.format("Could not find workflow instance with id '%s'.", workflowInstanceId));
    }
  }

  private WorkflowInstanceEntity fromSearchHit(String workflowInstanceString) {
    WorkflowInstanceEntity workflowInstance = null;
    try {
      workflowInstance = objectMapper.readValue(workflowInstanceString, WorkflowInstanceEntity.class);
    } catch (IOException e) {
      logger.error("Error while reading workflow instance from Elasticsearch!", e);
      throw new RuntimeException("Error while reading workflow instance from Elasticsearch!", e);
    }
    return workflowInstance;
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

      QueryBuilder activeOrIncidentsQuery = null;

      if (active && !incidents) {
        //active query
        activeOrIncidentsQuery = boolQuery().mustNot(nestedQuery(INCIDENTS, termQuery(INCIDENT_STATE_TERM, ACTIVE_INCIDENT), None));
      }
      else if (!active && incidents) {
        //incidents query
        activeOrIncidentsQuery = nestedQuery(INCIDENTS, termQuery(INCIDENT_STATE_TERM, ACTIVE_INCIDENT), None);
      }

      runningQuery = joinWithAnd(runningQuery, activeOrIncidentsQuery);
    }

    QueryBuilder finishedQuery = null;

    if (finished && (completed || canceled)) {

      //add finished query
      finishedQuery = existsQuery(END_DATE);

      QueryBuilder canceledOrCompletedQ = null;

      if (completed && !canceled) {
        //completed query
        canceledOrCompletedQ = termQuery(STATE, WorkflowInstanceState.COMPLETED.toString());
      }
      else if (!completed && canceled) {
        //add canceled query
        canceledOrCompletedQ = termQuery(STATE, WorkflowInstanceState.CANCELED.toString());
      }

      finishedQuery = joinWithAnd(finishedQuery, canceledOrCompletedQ);
    }

    final QueryBuilder workflowInstanceQuery = joinWithOr(runningQuery, finishedQuery);

    if (workflowInstanceQuery == null) {
      return createMatchNoneQuery();
    }

    return workflowInstanceQuery;

  }

}
