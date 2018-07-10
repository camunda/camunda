package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.rest.dto.SortingDto;
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
import static org.camunda.operate.es.types.WorkflowInstanceType.END_DATE;
import static org.camunda.operate.es.types.WorkflowInstanceType.INCIDENTS;
import static org.camunda.operate.es.types.WorkflowInstanceType.STATE;
import static org.camunda.operate.util.ElasticsearchUtil.createMatchNoneQuery;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class WorkflowInstanceReader {

  private Logger logger = LoggerFactory.getLogger(WorkflowInstanceReader.class);

  private static final String ACTIVE_INCIDENT = ACTIVE.toString();
  private static final String ACTIVE_INCIDENT_TERM = String.format("%s.%s", INCIDENTS, STATE);

  @Autowired
  private TransportClient esClient;

  @Autowired
  @Qualifier("esObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowInstanceType workflowInstanceType;

  /**
   * Counts workflow instances filtered by different criteria.
   * @param workflowInstanceQuery
   * @return
   */
  public long countWorkflowInstances(WorkflowInstanceQueryDto workflowInstanceQuery) {
    SearchResponse response = createSearchRequest(workflowInstanceQuery)
      .setFetchSource(false)
      .setSize(0)
      .get();

    return response.getHits().getTotalHits();
  }

  /**
   * Queries workflow instances by different criteria (with pagination).
   * @param workflowInstanceQuery
   * @param firstResult
   * @param maxResults
   * @return
   */
  public List<WorkflowInstanceEntity> queryWorkflowInstances(WorkflowInstanceQueryDto workflowInstanceQuery, Integer firstResult, Integer maxResults) {
    SearchRequestBuilder searchRequest = createSearchRequest(workflowInstanceQuery)
      .setFetchSource(null, WorkflowInstanceType.ACTIVITIES);

    if (firstResult != null && maxResults != null) {
      return paginate(searchRequest, firstResult, maxResults);
    }
    else {
      return scroll(searchRequest);
    }
  }

  protected SearchRequestBuilder createSearchRequest(WorkflowInstanceQueryDto queryDto) {

    final QueryBuilder query = createQuery(queryDto);

    ConstantScoreQueryBuilder constantScoreQuery = QueryBuilders.constantScoreQuery(query);

    logger.debug("Workflow instance search request: \n{}", constantScoreQuery.toString());

    final SearchRequestBuilder searchRequestBuilder = esClient.prepareSearch(workflowInstanceType.getType());
    applySorting(searchRequestBuilder, queryDto.getSorting());
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

  private QueryBuilder createQuery(WorkflowInstanceQueryDto queryDto) {
    final QueryBuilder runningFinishedQuery = createRunningFinishedQuery(queryDto);

    QueryBuilder workflowInstanceIdsQuery = null;
    if (queryDto.getWorkflowInstanceIds() != null && !queryDto.getWorkflowInstanceIds().isEmpty()) {
      workflowInstanceIdsQuery = createWorkflowInstanceIdsQuery(queryDto.getWorkflowInstanceIds());
    }

    //further parameters will be applied like this:
    //QueryBuilder workflowVersionQuery = createWorkflowVersionQuery(version);
    //...
    QueryBuilder query = joinWithAnd(runningFinishedQuery, workflowInstanceIdsQuery);

    return query;
  }

  private QueryBuilder createWorkflowInstanceIdsQuery(List<String> workflowInstanceIds) {
    return termsQuery(WorkflowInstanceType.ID, workflowInstanceIds);
  }

  protected List<WorkflowInstanceEntity> paginate(SearchRequestBuilder builder, int firstResult, int maxResults) {
    SearchResponse response = builder
      .setFrom(firstResult)
      .setSize(maxResults)
      .get();

    return mapSearchHits(response.getHits().getHits());
  }

  protected List<WorkflowInstanceEntity> scroll(SearchRequestBuilder builder) {
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

    return result;
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
        activeOrIncidentsQuery = boolQuery().mustNot(nestedQuery(INCIDENTS, termQuery(ACTIVE_INCIDENT_TERM, ACTIVE_INCIDENT), None));
      }
      else if (!active && incidents) {
        //incidents query
        activeOrIncidentsQuery = nestedQuery(INCIDENTS, termQuery(ACTIVE_INCIDENT_TERM, ACTIVE_INCIDENT), None);
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
