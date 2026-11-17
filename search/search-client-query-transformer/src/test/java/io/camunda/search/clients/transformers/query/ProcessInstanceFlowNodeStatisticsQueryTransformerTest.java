/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_ACTIVE;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_CANCELED;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_FILTER_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FILTERS;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_INCIDENTS;
import static io.camunda.search.aggregation.ProcessInstanceFlowNodeStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.clients.aggregator.SearchFiltersAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import org.junit.jupiter.api.Test;

public class ProcessInstanceFlowNodeStatisticsQueryTransformerTest {

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  protected <Q extends TypedSearchAggregationQuery> SearchQueryRequest transformQuery(
      final Q query) {
    return transformers.getTypedSearchQueryTransformer(query.getClass()).apply(query);
  }

  @Test
  public void shouldQueryByProcessInstanceKeyAndAggregation() {
    // given
    final var processInstanceKey = 123L;
    final var filter = new ProcessInstanceStatisticsFilter(processInstanceKey);
    final var statisticsQuery = new ProcessInstanceFlowNodeStatisticsQuery(filter);

    // when
    final var searchRequest = transformQuery(statisticsQuery);

    // then
    assertThat(searchRequest.sort()).isNull();
    assertThat(searchRequest.from()).isZero();
    assertThat(searchRequest.size()).isZero();

    // query: flat term on flownode-instance — no join relation filter
    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchTermQuery.class);
    final var termQuery = (SearchTermQuery) queryVariant;
    assertThat(termQuery).isEqualTo(term(PROCESS_INSTANCE_KEY, processInstanceKey).queryOption());

    // aggregation: flat filter → terms → filters (no children wrapper)
    final var aggregations = searchRequest.aggregations();
    assertThat(aggregations).hasSize(1);
    assertThat(aggregations.getFirst()).isInstanceOf(SearchFilterAggregator.class);

    final var filterAgg = (SearchFilterAggregator) aggregations.getFirst();
    assertThat(filterAgg.name()).isEqualTo(AGGREGATION_FILTER_FLOW_NODES);
    assertThat(filterAgg.query())
        .isEqualTo(
            or(not(term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.toString())), term(INCIDENT, true)));
    assertThat(filterAgg.aggregations()).hasSize(1);
    assertThat(filterAgg.aggregations().getFirst()).isInstanceOf(SearchTermsAggregator.class);

    final var termsAgg = (SearchTermsAggregator) filterAgg.aggregations().getFirst();
    assertThat(termsAgg.name()).isEqualTo(AGGREGATION_GROUP_FLOW_NODES);
    assertThat(termsAgg.field()).isEqualTo(FLOW_NODE_ID);
    assertThat(termsAgg.size()).isEqualTo(AGGREGATION_TERMS_SIZE);
    assertThat(termsAgg.minDocCount()).isEqualTo(1);
    assertThat(termsAgg.aggregations()).hasSize(1);
    assertThat(termsAgg.aggregations().getFirst()).isInstanceOf(SearchFiltersAggregator.class);

    final var filtersAgg = (SearchFiltersAggregator) termsAgg.aggregations().getFirst();
    assertThat(filtersAgg.name()).isEqualTo(AGGREGATION_GROUP_FILTERS);
    assertThat(filtersAgg.aggregations()).isNull();
    assertThat(filtersAgg.queries())
        .hasSize(4)
        .containsOnly(
            entry(
                AGGREGATION_ACTIVE,
                and(term(INCIDENT, false), term(STATE, FlowNodeState.ACTIVE.toString()))),
            entry(AGGREGATION_COMPLETED, term(STATE, FlowNodeState.COMPLETED.toString())),
            entry(AGGREGATION_CANCELED, term(STATE, FlowNodeState.TERMINATED.toString())),
            entry(
                AGGREGATION_INCIDENTS,
                and(term(INCIDENT, true), term(STATE, FlowNodeState.ACTIVE.toString()))));
  }
}
