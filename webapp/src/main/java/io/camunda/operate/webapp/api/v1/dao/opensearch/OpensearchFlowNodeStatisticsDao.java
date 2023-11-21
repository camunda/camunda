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
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.springframework.beans.factory.annotation.Autowired;
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
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.not;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ElasticsearchUtil.TERMS_AGG_SIZE;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsDao extends OpensearchDao implements FlowNodeStatisticsDao {
  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  public OpensearchFlowNodeStatisticsDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                       RichOpenSearchClient richOpenSearchClient) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
  }

  @Override
  public List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(Long processInstanceKey) {
    var requestBuilder = searchRequestBuilder(flowNodeInstanceTemplate)
      .query(withTenantCheck(constantScore(term(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
      .aggregations(FLOW_NODE_ID_AGG, withSubaggregations(
        termAggregation(FLOW_NODE_ID, TERMS_AGG_SIZE),
        Map.of(
              COUNT_INCIDENT, term(INCIDENT, true)._toAggregation(),
              COUNT_CANCELED, and(
                not(term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                term(STATE, TERMINATED.name())
              )._toAggregation(),
              COUNT_COMPLETED, and(
                not(term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                term(STATE, COMPLETED.name())
              )._toAggregation(),
              COUNT_ACTIVE, and(
                not(term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                term(STATE, ACTIVE.name()),
                term(INCIDENT, false)
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
