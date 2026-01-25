/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_ACTIVE;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_CANCELED;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_COMPLETED;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_INCIDENTS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FILTERS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODE_ID;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_PARENT_PI;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filters;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.parent;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;

import io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class ProcessDefinitionFlowNodeStatisticsAggregationTransformer
    implements AggregationTransformer<ProcessDefinitionFlowNodeStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<ProcessDefinitionFlowNodeStatisticsAggregation, ServiceTransformers> value) {

    // aggregate parent process instances (count distinct process instances)
    final var parentAgg = parent(AGGREGATION_TO_PARENT_PI, ACTIVITIES_JOIN_RELATION);

    // aggregate filters for active, completed, canceled, incidents
    // each filter bucket will have a parent aggregation to count distinct process instances
    final var filtersAgg =
        filters()
            .name(AGGREGATION_GROUP_FILTERS)
            .namedQuery(
                AGGREGATION_FILTER_ACTIVE,
                and(term(INCIDENT, false), term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
            .namedQuery(
                AGGREGATION_FILTER_COMPLETED,
                term(ACTIVITY_STATE, FlowNodeState.COMPLETED.toString()))
            .namedQuery(
                AGGREGATION_FILTER_CANCELED,
                term(ACTIVITY_STATE, FlowNodeState.TERMINATED.toString()))
            .namedQuery(
                AGGREGATION_FILTER_INCIDENTS,
                and(term(INCIDENT, true), term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
            .aggregations(parentAgg)
            .build();

    // aggregate terms flow node id - groups by activityId first
    final var groupFlowNodesAgg =
        terms()
            .name(AGGREGATION_GROUP_FLOW_NODE_ID)
            .field(ACTIVITY_ID)
            .size(AGGREGATION_TERMS_SIZE)
            .aggregations(filtersAgg)
            .build();

    return List.of(groupFlowNodesAgg);
  }
}
