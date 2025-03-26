/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TYPE;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.ACTIVE;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.COMPLETED;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.TERMINATED;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeStatisticsDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.operate.webapp.opensearch.OpensearchAggregationDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsDao implements FlowNodeStatisticsDao {
  private static final int TERMS_AGG_SIZE = 10000;
  private final FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  private final RichOpenSearchClient richOpenSearchClient;
  private final OpensearchQueryDSLWrapper queryDSLWrapper;
  private final OpensearchRequestDSLWrapper requestDSLWrapper;
  private final OpensearchAggregationDSLWrapper aggregationDSLWrapper;

  public OpensearchFlowNodeStatisticsDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final OpensearchAggregationDSLWrapper aggregationDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final @Qualifier("operateFlowNodeInstanceTemplate") FlowNodeInstanceTemplate
              flowNodeInstanceTemplate) {
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
    this.richOpenSearchClient = richOpenSearchClient;
    this.queryDSLWrapper = queryDSLWrapper;
    this.requestDSLWrapper = requestDSLWrapper;
    this.aggregationDSLWrapper = aggregationDSLWrapper;
  }

  @Override
  public List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(
      final Long processInstanceKey) {
    final var requestBuilder =
        requestDSLWrapper
            .searchRequestBuilder(flowNodeInstanceTemplate)
            .query(
                queryDSLWrapper.withTenantCheck(
                    queryDSLWrapper.constantScore(
                        queryDSLWrapper.term(
                            FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
            .aggregations(
                FLOW_NODE_ID_AGG,
                aggregationDSLWrapper.withSubaggregations(
                    aggregationDSLWrapper.termAggregation(FLOW_NODE_ID, TERMS_AGG_SIZE),
                    Map.of(
                        COUNT_INCIDENT, queryDSLWrapper.term(INCIDENT, true)._toAggregation(),
                        COUNT_CANCELED,
                            queryDSLWrapper
                                .and(
                                    queryDSLWrapper.not(
                                        queryDSLWrapper.term(
                                            TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    queryDSLWrapper.term(STATE, TERMINATED.name()))
                                ._toAggregation(),
                        COUNT_COMPLETED,
                            queryDSLWrapper
                                .and(
                                    queryDSLWrapper.not(
                                        queryDSLWrapper.term(
                                            TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    queryDSLWrapper.term(STATE, COMPLETED.name()))
                                ._toAggregation(),
                        COUNT_ACTIVE,
                            queryDSLWrapper
                                .and(
                                    queryDSLWrapper.not(
                                        queryDSLWrapper.term(
                                            TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    queryDSLWrapper.term(STATE, ACTIVE.name()),
                                    queryDSLWrapper.term(INCIDENT, false))
                                ._toAggregation())))
            .size(0);

    return richOpenSearchClient
        .doc()
        .search(requestBuilder, Void.class)
        .aggregations()
        .get(FLOW_NODE_ID_AGG)
        .sterms()
        .buckets()
        .array()
        .stream()
        .map(
            bucket ->
                new FlowNodeStatistics()
                    .setActivityId(bucket.key())
                    .setCanceled(bucket.aggregations().get(COUNT_CANCELED).filter().docCount())
                    .setIncidents(bucket.aggregations().get(COUNT_INCIDENT).filter().docCount())
                    .setCompleted(bucket.aggregations().get(COUNT_COMPLETED).filter().docCount())
                    .setActive(bucket.aggregations().get(COUNT_ACTIVE).filter().docCount()))
        .toList();
  }
}
