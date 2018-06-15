package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.po.IncidentState;
import org.camunda.operate.po.WorkflowInstanceEntity;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Profile("elasticsearch")
public class WorkflowInstanceReaderImpl implements WorkflowInstanceReader {

  private Logger logger = LoggerFactory.getLogger(WorkflowInstanceReaderImpl.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public long countWorkflowInstances(WorkflowInstanceQueryDto workflowInstanceQuery) {
    SearchResponse response = createSearchRequest(workflowInstanceQuery)
      .setFetchSource(false)
      .setSize(0)
      .get();

    return response.getHits().getTotalHits();
  }

  @Override
  public List<WorkflowInstanceEntity> queryWorkflowInstances(WorkflowInstanceQueryDto workflowInstanceQuery, Integer firstResult, Integer maxResults) {
    SearchRequestBuilder searchRequest = createSearchRequest(workflowInstanceQuery);

    if (firstResult != null && maxResults != null) {
      return paginate(searchRequest, firstResult, maxResults);
    }
    else {
      return scroll(searchRequest);
    }
  }

  protected SearchRequestBuilder createSearchRequest(WorkflowInstanceQueryDto queryDto) {
    BoolQueryBuilder query = QueryBuilders.boolQuery();
    applyQueryParameter(query, queryDto);

    ConstantScoreQueryBuilder constantScoreQuery = QueryBuilders.constantScoreQuery(query);

    return esClient
        .prepareSearch(WorkflowInstanceType.TYPE)
        .setQuery(constantScoreQuery);
  }

  protected List<WorkflowInstanceEntity> paginate(SearchRequestBuilder builder, int firstResult, int maxResults) {
    SearchResponse response = builder
      .setFrom(firstResult)
      .setSize(maxResults)
      .get();

    List<WorkflowInstanceEntity> result = new ArrayList<>();
    mapSearchHits(response.getHits().getHits(), result);
    return result;
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

      mapSearchHits(hits.getHits(), result);

      response = esClient
          .prepareSearchScroll(scrollId)
          .setScroll(keepAlive)
          .get();

    } while (response.getHits().getHits().length != 0);

    return result;
  }

  protected void mapSearchHits(SearchHit[] searchHits, List<WorkflowInstanceEntity> result) {
    for (SearchHit searchHit : searchHits) {
      String searchHitAsString = searchHit.getSourceAsString();
      result.add(fromSearchHit(searchHitAsString));
    }
  }

  @Override
  public WorkflowInstanceEntity getWorkflowInstanceById(String workflowInstanceId) {
    final GetResponse getResponse = esClient.prepareGet(WorkflowInstanceType.TYPE, WorkflowInstanceType.TYPE, workflowInstanceId).get();
    return fromSearchHit(getResponse.getSourceAsString());
  }

  private WorkflowInstanceEntity fromSearchHit(String workflowInstanceString) {
    WorkflowInstanceEntity workflowInstance = null;
    try {
      workflowInstance = objectMapper.readValue(workflowInstanceString, WorkflowInstanceEntity.class);
    } catch (IOException e) {
      logger.error("Error while reading workflow instance from elastic search!", e);
    }
    return workflowInstance;
  }

  private void applyQueryParameter(BoolQueryBuilder query, WorkflowInstanceQueryDto workflowInstanceQuery) {
    if (query != null && workflowInstanceQuery != null) {
      if (workflowInstanceQuery.isCompleted()) {
        query.must(QueryBuilders.existsQuery(WorkflowInstanceType.END_DATE));
      }
      if (workflowInstanceQuery.isRunning()) {
        query.mustNot(QueryBuilders.existsQuery(WorkflowInstanceType.END_DATE));
      }
      if (workflowInstanceQuery.isWithIncidents()) {
        query.must(QueryBuilders.nestedQuery(WorkflowInstanceType.INCIDENTS, QueryBuilders.termQuery(
          String.format("%s.%s", WorkflowInstanceType.INCIDENTS, WorkflowInstanceType.STATE), IncidentState.ACTIVE.toString()), ScoreMode.None));
      }
      if (workflowInstanceQuery.isWithoutIncidents()) {
        query.mustNot(QueryBuilders.nestedQuery(WorkflowInstanceType.INCIDENTS, QueryBuilders.termQuery(
          String.format("%s.%s", WorkflowInstanceType.INCIDENTS, WorkflowInstanceType.STATE), IncidentState.ACTIVE.toString()), ScoreMode.None));
      }
    }
  }

}
