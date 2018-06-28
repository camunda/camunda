package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.types.WorkflowInstanceType;
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

    return esClient
        .prepareSearch(workflowInstanceType.getType())
        .setQuery(constantScoreQuery);
  }

  private QueryBuilder createQuery(WorkflowInstanceQueryDto queryDto) {
    final QueryBuilder runningFinishedQuery = createRunningFinishedQuery(queryDto);

    //further parameters will be applied like this:
    //QueryBuilder workflowNameQuery = createWorkflowNameQuery(name);
    //QueryBuilder workflowVersionQuery = createWorkflowVersionQuery(version);
    //...
    //return joinWithAnd(runningFinishedQuery, workflowNameQuery, workflowVersionQuery, ...);

    return runningFinishedQuery;
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

    if (! query.isRunning() && ! query.isFinished()) {
      //empty list should be returned
      return createMatchNoneQuery();
    }

    QueryBuilder runningQ = null;
    if (query.isRunning()) {

      if (!query.isActive() && !query.isIncidents()) {
        //do nothing, do not need to include any running instances
      } else {

        //running query
        runningQ = boolQuery().mustNot(existsQuery(END_DATE));

        if (!(query.isActive() && query.isIncidents())) {   //    if both are true - no additional filters are needed
          QueryBuilder activeOrIncidentsQ = null;
          if (query.isActive()) {
            //active query
            activeOrIncidentsQ = boolQuery().mustNot(nestedQuery(INCIDENTS, termQuery(ACTIVE_INCIDENT_TERM, ACTIVE_INCIDENT), None));
          } else if (query.isIncidents()) {
            //incidents query
            activeOrIncidentsQ = nestedQuery(INCIDENTS, termQuery(ACTIVE_INCIDENT_TERM, ACTIVE_INCIDENT), None);
          }
          runningQ = joinWithAnd(runningQ, activeOrIncidentsQ);
        }

      }
    }

    QueryBuilder finishedQ = null;
    if (query.isFinished()) {

      if (!query.isCompleted() && !query.isCancelled()) {
        // do nothing
      } else {
        //add finished query
        finishedQ = existsQuery(END_DATE);

        if (!(query.isCompleted() && query.isCancelled())) {      //if both true - no additional filters are needed
          QueryBuilder cancelledOrCompletedQ = null;
          if (query.isCompleted()) {
            //completed query
            cancelledOrCompletedQ = termQuery(STATE, WorkflowInstanceState.COMPLETED.toString());
          } else if (query.isCancelled()) {
            //add cancelled query
            cancelledOrCompletedQ = termQuery(STATE, WorkflowInstanceState.CANCELED.toString());
          }

          finishedQ = joinWithAnd(finishedQ, cancelledOrCompletedQ);

        }

      }

    }

    final QueryBuilder runningOrFinishedQ = joinWithOr(runningQ, finishedQ);
    if (runningOrFinishedQ == null) {
      return createMatchNoneQuery();
    }
    return runningOrFinishedQ;

  }

}
