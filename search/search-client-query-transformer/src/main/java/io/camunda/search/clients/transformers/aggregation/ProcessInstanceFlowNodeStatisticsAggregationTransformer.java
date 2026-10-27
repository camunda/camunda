/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_ACTIVE;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_CANCELED;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_FILTER_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FILTERS;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_INCIDENTS;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filter;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filters;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class ProcessInstanceFlowNodeStatisticsAggregationTransformer
    implements AggregationTransformer<ProcessInstanceFlowNodeStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<ProcessInstanceFlowNodeStatisticsAggregation, ServiceTransformers> value) {

    // aggregate filters for active, completed, canceled, incidents
    final var filtersAgg =
        filters()
            .name(AGGREGATION_GROUP_FILTERS)
            .namedQuery(
                AGGREGATION_ACTIVE,
                and(
                    term(FlowNodeInstanceTemplate.INCIDENT, false),
                    term(FlowNodeInstanceTemplate.STATE, FlowNodeState.ACTIVE.toString())))
            .namedQuery(
                AGGREGATION_COMPLETED,
                term(FlowNodeInstanceTemplate.STATE, FlowNodeState.COMPLETED.toString()))
            .namedQuery(
                AGGREGATION_CANCELED,
                term(FlowNodeInstanceTemplate.STATE, FlowNodeState.TERMINATED.toString()))
            .namedQuery(
                AGGREGATION_INCIDENTS,
                and(
                    term(FlowNodeInstanceTemplate.INCIDENT, true),
                    term(FlowNodeInstanceTemplate.STATE, FlowNodeState.ACTIVE.toString())))
            .build();

    // aggregate by flow node id
    final var groupFlowNodesAgg =
        terms()
            .name(AGGREGATION_GROUP_FLOW_NODES)
            .field(FlowNodeInstanceTemplate.FLOW_NODE_ID)
            .size(AGGREGATION_TERMS_SIZE)
            .aggregations(filtersAgg)
            .build();

    // exclude multi-instance bodies UNLESS they have incidents
    // so that incidents on the multi-instance body itself are counted
    final var filterFlowNodesAgg =
        filter()
            .name(AGGREGATION_FILTER_FLOW_NODES)
            .query(
                or(
                    not(
                        term(
                            FlowNodeInstanceTemplate.TYPE,
                            FlowNodeType.MULTI_INSTANCE_BODY.toString())),
                    term(FlowNodeInstanceTemplate.INCIDENT, true)))
            .aggregations(groupFlowNodesAgg)
            .build();

    return List.of(filterFlowNodesAgg);
  }
}
