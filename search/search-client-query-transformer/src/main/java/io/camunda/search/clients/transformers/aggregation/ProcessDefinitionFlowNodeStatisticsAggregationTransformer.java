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
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_INCIDENTS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODE_ID;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_CHILDREN_FN;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_PARENT_PI;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.children;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filter;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.parent;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;
import static java.util.Optional.ofNullable;

import io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import java.util.ArrayList;
import java.util.List;

public class ProcessDefinitionFlowNodeStatisticsAggregationTransformer
    implements AggregationTransformer<ProcessDefinitionFlowNodeStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(final ProcessDefinitionFlowNodeStatisticsAggregation value) {

    // aggregate parent process instances
    final var filtersAgg = parent(AGGREGATION_TO_PARENT_PI, ACTIVITIES_JOIN_RELATION);

    // aggregate terms flow node id
    final var groupFlowNodesAgg =
        terms()
            .name(AGGREGATION_GROUP_FLOW_NODE_ID)
            .field(ACTIVITY_ID)
            .size(AGGREGATION_TERMS_SIZE)
            .aggregations(filtersAgg)
            .build();

    // aggregate filter active
    final var activeAgg =
        filter()
            .name(AGGREGATION_FILTER_ACTIVE)
            .query(
                and(term(INCIDENT, false), term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
            .aggregations(groupFlowNodesAgg)
            .build();

    // aggregate filter completed
    final var completedAgg =
        filter()
            .name(AGGREGATION_FILTER_COMPLETED)
            .query(term(ACTIVITY_STATE, FlowNodeState.COMPLETED.toString()))
            .aggregations(groupFlowNodesAgg)
            .build();

    // aggregate filter canceled
    final var canceledAgg =
        filter()
            .name(AGGREGATION_FILTER_CANCELED)
            .query(term(ACTIVITY_STATE, FlowNodeState.TERMINATED.toString()))
            .aggregations(groupFlowNodesAgg)
            .build();

    // aggregate filter incidents
    final var incidentsAgg =
        filter()
            .name(AGGREGATION_FILTER_INCIDENTS)
            .query(and(term(INCIDENT, true), term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
            .aggregations(groupFlowNodesAgg)
            .build();

    final var filter = value.filter();
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(stringOperations(ACTIVITY_ID, filter.flowNodeIdOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringOperations(ACTIVITY_STATE, filter.flowNodeInstanceStateOperations()))
        .ifPresent(queries::addAll);
    ofNullable(filter.hasFlowNodeInstanceIncident())
        .ifPresent(incident -> queries.add((term(INCIDENT, incident))));

    // aggregate filter flow nodes
    final var filterFlowNodesAgg =
        filter()
            .name(AGGREGATION_FILTER_FLOW_NODES)
            .query(queries.isEmpty() ? matchAll() : and(queries))
            .aggregations(activeAgg, completedAgg, canceledAgg, incidentsAgg)
            .build();

    // aggregate children flow nodes
    final var toFlowNodesAgg =
        children()
            .name(AGGREGATION_TO_CHILDREN_FN)
            .type(ACTIVITIES_JOIN_RELATION)
            .aggregations(filterFlowNodesAgg)
            .build();

    return List.of(toFlowNodesAgg);
  }
}
