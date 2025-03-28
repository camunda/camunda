/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.children;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filter;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filters;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery.*;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.NoSort;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import java.util.List;

public class ProcessDefinitionFlowNodeStatisticsQueryTransformer
    extends TypedSearchAggregationQueryTransformer<ProcessDefinitionStatisticsFilter> {

  public ProcessDefinitionFlowNodeStatisticsQueryTransformer(
      final ServiceTransformers transformers) {
    super(transformers);
  }

  @Override
  protected List<SearchAggregator> applyAggregations() {
    // aggregate filters for active, completed, canceled, incidents
    final var filtersAgg =
        filters()
            .name(AGGREGATION_GROUP_FILTERS)
            .namedQuery(
                AGGREGATION_ACTIVE,
                and(
                    term(ListViewTemplate.INCIDENT, false),
                    term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
            .namedQuery(
                AGGREGATION_COMPLETED,
                and(
                    term(ListViewTemplate.ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString()),
                    term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.COMPLETED.toString())))
            .namedQuery(
                AGGREGATION_CANCELED,
                term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.TERMINATED.toString()))
            .namedQuery(
                AGGREGATION_INCIDENTS,
                and(
                    term(ListViewTemplate.INCIDENT, true),
                    term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
            .build();

    // aggregate by flow node id
    final var groupFlowNodesAgg =
        terms()
            .name(AGGREGATION_GROUP_FLOW_NODES)
            .field(ListViewTemplate.ACTIVITY_ID)
            .size(AGGREGATION_TERMS_SIZE)
            .aggregations(filtersAgg)
            .build();

    // aggregate filter flow nodes
    final var filterFlowNodesAgg =
        filter()
            .name(AGGREGATION_FILTER_FLOW_NODES)
            .query(
                or(
                    not(term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.COMPLETED.toString())),
                    term(ListViewTemplate.ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString())))
            .aggregations(groupFlowNodesAgg)
            .build();

    // aggregate flow node children
    final var toFlowNodesAgg =
        children()
            .name(AGGREGATION_TO_FLOW_NODES)
            .type(ListViewTemplate.ACTIVITIES_JOIN_RELATION)
            .aggregations(filterFlowNodesAgg)
            .build();

    return List.of(toFlowNodesAgg);
  }
}
