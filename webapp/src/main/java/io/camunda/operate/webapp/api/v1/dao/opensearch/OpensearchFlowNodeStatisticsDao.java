/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeStatisticsDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.operate.webapp.opensearch.OpensearchAggregationDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.camunda.operate.entities.FlowNodeState.ACTIVE;
import static io.camunda.operate.entities.FlowNodeState.COMPLETED;
import static io.camunda.operate.entities.FlowNodeState.TERMINATED;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.STATE;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TYPE;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsDao implements FlowNodeStatisticsDao {
  private final FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  private final RichOpenSearchClient richOpenSearchClient;
  private final OpensearchQueryDSLWrapper queryDSLWrapper;
  private final OpensearchRequestDSLWrapper requestDSLWrapper;
  private final OpensearchAggregationDSLWrapper aggregationDSLWrapper;

  private static final int TERMS_AGG_SIZE = 10000;

  public OpensearchFlowNodeStatisticsDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                         OpensearchAggregationDSLWrapper aggregationDSLWrapper,
                                         RichOpenSearchClient richOpenSearchClient, FlowNodeInstanceTemplate flowNodeInstanceTemplate) {
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
    this.richOpenSearchClient = richOpenSearchClient;
    this.queryDSLWrapper = queryDSLWrapper;
    this.requestDSLWrapper = requestDSLWrapper;
    this.aggregationDSLWrapper = aggregationDSLWrapper;
  }

  @Override
  public List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(Long processInstanceKey) {
    var requestBuilder = requestDSLWrapper.searchRequestBuilder(flowNodeInstanceTemplate)
      .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.constantScore(queryDSLWrapper.term(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
      .aggregations(FLOW_NODE_ID_AGG, aggregationDSLWrapper.withSubaggregations(
        aggregationDSLWrapper.termAggregation(FLOW_NODE_ID, TERMS_AGG_SIZE),
        Map.of(
              COUNT_INCIDENT, queryDSLWrapper.term(INCIDENT, true)._toAggregation(),
              COUNT_CANCELED, queryDSLWrapper.and(
                queryDSLWrapper.not(queryDSLWrapper.term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                queryDSLWrapper.term(STATE, TERMINATED.name())
              )._toAggregation(),
              COUNT_COMPLETED, queryDSLWrapper.and(
                queryDSLWrapper.not(queryDSLWrapper.term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                queryDSLWrapper.term(STATE, COMPLETED.name())
              )._toAggregation(),
              COUNT_ACTIVE, queryDSLWrapper.and(
                queryDSLWrapper.not(queryDSLWrapper.term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                queryDSLWrapper.term(STATE, ACTIVE.name()),
                queryDSLWrapper.term(INCIDENT, false)
              )._toAggregation()
          )
        )
      )
      .size(0);

    return richOpenSearchClient.doc().search(requestBuilder, Void.class)
      .aggregations()
      .get(FLOW_NODE_ID_AGG)
      .sterms()
      .buckets()
      .array()
      .stream()
      .map(bucket ->
        new FlowNodeStatistics()
          .setActivityId(bucket.key())
          .setCanceled(bucket.aggregations().get(COUNT_CANCELED).filter().docCount())
          .setIncidents(bucket.aggregations().get(COUNT_INCIDENT).filter().docCount())
          .setCompleted(bucket.aggregations().get(COUNT_COMPLETED).filter().docCount())
          .setActive(bucket.aggregations().get(COUNT_ACTIVE).filter().docCount())
      ).toList();
  }
}
