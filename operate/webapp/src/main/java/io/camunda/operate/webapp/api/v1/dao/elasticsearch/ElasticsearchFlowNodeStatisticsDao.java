/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.TERMS_AGG_SIZE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TYPE;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.ACTIVE;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.COMPLETED;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.TERMINATED;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeStatisticsDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchFlowNodeStatisticsDaoV1")
public class ElasticsearchFlowNodeStatisticsDao extends ElasticsearchDao<FlowNodeStatistics>
    implements FlowNodeStatisticsDao {

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Override
  protected void buildFiltering(
      final Query<FlowNodeStatistics> query,
      final Builder searchRequestBuilder,
      final boolean isTenantAware) {
    final var filter = query.getFilter();

    if (filter == null) {
      final var finalQuery =
          isTenantAware
              ? tenantHelper.makeQueryTenantAware(ElasticsearchUtil.matchAllQuery())
              : ElasticsearchUtil.matchAllQuery();
      searchRequestBuilder.query(finalQuery);
      return;
    }

    final var activityIdQ =
        buildIfPresent(
            FlowNodeStatistics.ACTIVITY_ID, filter.getActivityId(), ElasticsearchUtil::termsQuery);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(activityIdQ) : activityIdQ;

    searchRequestBuilder.query(finalQuery);
  }

  @Override
  public List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(
      final Long processInstanceKey) {
    final var incidentCountAgg =
        new Aggregation.Builder()
            .filter(f -> f.bool(b -> b.must(ElasticsearchUtil.termsQuery(INCIDENT, true))))
            .build();

    final var cancelledCountAgg =
        new Aggregation.Builder()
            .filter(
                f ->
                    f.bool(
                        b ->
                            b.mustNot(
                                    ElasticsearchUtil.termsQuery(
                                        TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                .must(ElasticsearchUtil.termsQuery(STATE, TERMINATED))))
            .build();

    final var completedCountAgg =
        new Aggregation.Builder()
            .filter(
                f ->
                    f.bool(
                        b ->
                            b.mustNot(
                                    ElasticsearchUtil.termsQuery(
                                        TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                .must(ElasticsearchUtil.termsQuery(STATE, COMPLETED))))
            .build();

    final var activeCountAgg =
        new Aggregation.Builder()
            .filter(
                f ->
                    f.bool(
                        b ->
                            b.mustNot(
                                    ElasticsearchUtil.termsQuery(
                                        TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                .must(ElasticsearchUtil.termsQuery(STATE, ACTIVE))
                                .must(ElasticsearchUtil.termsQuery(INCIDENT, false))))
            .build();

    final var subAggs =
        Map.of(
            COUNT_INCIDENT, incidentCountAgg,
            COUNT_CANCELED, cancelledCountAgg,
            COUNT_COMPLETED, completedCountAgg,
            COUNT_ACTIVE, activeCountAgg);

    final var query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.termsQuery(
                FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
    final var request =
        new SearchRequest.Builder()
            .index(flowNodeInstanceTemplate.getAlias())
            .query(tenantAwareQuery)
            .aggregations(
                FLOW_NODE_ID_AGG,
                a -> a.terms(t -> t.field(FLOW_NODE_ID).size(TERMS_AGG_SIZE)).aggregations(subAggs))
            .size(0)
            .build();

    try {
      final var response = esClient.search(request, Void.class);

      final var flowNodeAgg = response.aggregations().get(FLOW_NODE_ID_AGG).sterms();

      return flowNodeAgg.buckets().array().stream()
          .map(
              bucket -> {
                final var aggs = bucket.aggregations();
                return new FlowNodeStatistics()
                    .setActivityId(bucket.key().stringValue())
                    .setCanceled(aggs.get(COUNT_CANCELED).filter().docCount())
                    .setIncidents(aggs.get(COUNT_INCIDENT).filter().docCount())
                    .setCompleted(aggs.get(COUNT_COMPLETED).filter().docCount())
                    .setActive(aggs.get(COUNT_ACTIVE).filter().docCount());
              })
          .collect(Collectors.toList());
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining statistics for process instance flow nodes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
