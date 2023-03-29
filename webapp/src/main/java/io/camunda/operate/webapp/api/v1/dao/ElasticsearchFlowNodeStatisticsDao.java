/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.entities.FlowNodeState.ACTIVE;
import static io.camunda.operate.entities.FlowNodeState.COMPLETED;
import static io.camunda.operate.entities.FlowNodeState.TERMINATED;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.STATE;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TYPE;
import static io.camunda.operate.util.ElasticsearchUtil.TERMS_AGG_SIZE;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.operate.webapp.api.v1.entities.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("ElasticsearchFlowNodeStatisticsDaoV1")
public class ElasticsearchFlowNodeStatisticsDao extends ElasticsearchDao<FlowNodeStatistics> implements FlowNodeStatisticsDao {

  public static final String FLOW_NODE_ID_AGG = "flowNodeIdAgg";
  public static final String COUNT_INCIDENT = "countIncident";
  public static final String COUNT_CANCELED = "countCanceled";
  public static final String COUNT_COMPLETED = "countCompleted";
  public static final String COUNT_ACTIVE = "countActive";

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Override
  protected void buildFiltering(Query<FlowNodeStatistics> query, SearchSourceBuilder searchSourceBuilder) {

    final FlowNodeStatistics filter = query.getFilter();
    List<QueryBuilder> queryBuilders = new ArrayList<>();
    if (filter != null) {
      queryBuilders.add(buildTermQuery(FlowNodeStatistics.ACTIVITY_ID, filter.getActivityId()));
    }
    searchSourceBuilder.query(joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
  }

  @Override
  public List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(Long processInstanceKey) {
    try {
      final SearchRequest request = ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
          .source(new SearchSourceBuilder().query(constantScoreQuery(termQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)))
              .aggregation(terms(FLOW_NODE_ID_AGG).field(FLOW_NODE_ID)
                  .size(TERMS_AGG_SIZE)
                  .subAggregation(filter(COUNT_INCIDENT, boolQuery()
                      // Need to count when MULTI_INSTANCE_BODY itself has an incident
                      // .mustNot(termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                      .must(termQuery(INCIDENT, true))))
                  .subAggregation(
                      filter(COUNT_CANCELED, boolQuery().mustNot(termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY)).must(termQuery(STATE, TERMINATED))))
                  .subAggregation(
                      filter(COUNT_COMPLETED, boolQuery().mustNot(termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY)).must(termQuery(STATE, COMPLETED))))
                  .subAggregation(filter(COUNT_ACTIVE,
                      boolQuery().mustNot(termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY)).must(termQuery(STATE, ACTIVE)).must(termQuery(INCIDENT, false)))))
              .size(0));
      final SearchResponse response = elasticsearch.search(request, RequestOptions.DEFAULT);
      final Aggregations aggregations = response.getAggregations();
      final Terms flowNodeAgg = aggregations.get(FLOW_NODE_ID_AGG);
      return flowNodeAgg.getBuckets().stream().map(bucket -> new FlowNodeStatistics().setActivityId(bucket.getKeyAsString())
              .setCanceled(((Filter) bucket.getAggregations().get(COUNT_CANCELED)).getDocCount())
              .setIncidents(((Filter) bucket.getAggregations().get(COUNT_INCIDENT)).getDocCount())
              .setCompleted(((Filter) bucket.getAggregations().get(COUNT_COMPLETED)).getDocCount())
              .setActive(((Filter) bucket.getAggregations().get(COUNT_ACTIVE)).getDocCount())).collect(Collectors.toList());
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining statistics for process instance flow nodes: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
