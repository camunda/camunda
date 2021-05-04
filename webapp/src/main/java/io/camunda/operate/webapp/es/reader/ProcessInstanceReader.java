/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import org.apache.lucene.search.join.ScoreMode;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceCoreStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.join.query.HasChildQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(ProcessInstanceReader.class);

  public static final FilterAggregationBuilder INCIDENTS_AGGREGATION = AggregationBuilders.filter(
      "incidents",
      new HasChildQueryBuilder(
           ListViewTemplate.ACTIVITIES_JOIN_RELATION,
           QueryBuilders.existsQuery(ListViewTemplate.INCIDENT_KEY),
           ScoreMode.None
      )
  );

  public static final FilterAggregationBuilder RUNNING_AGGREGATION = AggregationBuilders.filter(
      "running",
      termQuery(
          ListViewTemplate.STATE,
          ProcessInstanceState.ACTIVE
       )
  );

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OperationReader operationReader;

  /**
   *
   * @param processDefinitionKey
   * @return
   */
//  public List<Long> queryProcessInstancesWithEmptyProcessVersion(Long processDefinitionKey) {
//      QueryBuilder queryBuilder = constantScoreQuery(
//          joinWithAnd(
//              termQuery(ListViewTemplate.PROCESS_KEY, processDefinitionKey),
//              boolQuery().mustNot(existsQuery(ListViewTemplate.PROCESS_VERSION))
//          )
//      );
//      SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
//                                      .source(new SearchSourceBuilder()
//                                      .query(queryBuilder)
//                                      .fetchSource(false));
//      try {
//        return ElasticsearchUtil.scrollKeysToList(searchRequest, esClient);
//      } catch (IOException e) {
//        final String message = String.format("Exception occurred, while obtaining process instance that has empty versions: %s", e.getMessage());
//        logger.error(message, e);
//        throw new OperateRuntimeException(message, e);
//      }
//  }

  /**
   * Searches for process instance by key.
   * @param processInstanceKey
   * @return
   */
  public ListViewProcessInstanceDto getProcessInstanceWithOperationsByKey(Long processInstanceKey) {
    try {
      final ProcessInstanceForListViewEntity processInstance = searchProcessInstanceByKey(processInstanceKey);

      return ListViewProcessInstanceDto.createFrom(processInstance,
            activityInstanceWithIncidentExists(processInstanceKey),
            operationReader.getOperationsByProcessInstanceKey(processInstanceKey)
      );
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance with operations: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Searches for process instance by key.
   * @param processInstanceKey
   * @return
   */
  public ProcessInstanceForListViewEntity getProcessInstanceByKey(Long processInstanceKey) {
    try {
      final ProcessInstanceForListViewEntity processInstance = searchProcessInstanceByKey(processInstanceKey);
      if (activityInstanceWithIncidentExists(processInstanceKey)) {
          processInstance.setState(ProcessInstanceState.INCIDENT);
      }
      return processInstance;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  protected ProcessInstanceForListViewEntity searchProcessInstanceByKey(Long processInstanceKey) throws IOException {
    final QueryBuilder query = joinWithAnd(
        idsQuery().addIds(String.valueOf(processInstanceKey)),
        termQuery(ListViewTemplate.PROCESS_INSTANCE_KEY,processInstanceKey)
    );

    SearchRequest request = ElasticsearchUtil.createSearchRequest(listViewTemplate, ALL)
      .source(new SearchSourceBuilder()
      .query(constantScoreQuery(query)));

    final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
    if (response.getHits().getTotalHits().value == 1) {
       final ProcessInstanceForListViewEntity processInstance = ElasticsearchUtil
          .fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, ProcessInstanceForListViewEntity.class);
        return processInstance;
    } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique process instance with id '%s'.", processInstanceKey));
    } else {
        throw new NotFoundException(String.format("Could not find process instance with id '%s'.", processInstanceKey));
    }
  }

  private boolean activityInstanceWithIncidentExists(Long processInstanceKey) throws IOException {

    final TermQueryBuilder processInstanceKeyQuery = termQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final ExistsQueryBuilder existsIncidentQ = existsQuery(ListViewTemplate.INCIDENT_KEY);

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, ONLY_RUNTIME)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(joinWithAnd(processInstanceKeyQuery, existsIncidentQ)))
        .fetchSource(ListViewTemplate.ID, null));
    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return response.getHits().getTotalHits().value > 0;
  }

  public ProcessInstanceCoreStatisticsDto getCoreStatistics() {
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().size(0)
            .aggregation(INCIDENTS_AGGREGATION)
            .aggregation(RUNNING_AGGREGATION)
    );

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      Aggregations aggregations = response.getAggregations();
      long runningCount = ((SingleBucketAggregation) aggregations.get("running")).getDocCount();
      long incidentCount = ((SingleBucketAggregation) aggregations.get("incidents")).getDocCount();
      ProcessInstanceCoreStatisticsDto processInstanceCoreStatisticsDto = new ProcessInstanceCoreStatisticsDto().setRunning(runningCount)
          .setActive(runningCount - incidentCount).setWithIncidents(incidentCount);
      return processInstanceCoreStatisticsDto;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance core statistics: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}
