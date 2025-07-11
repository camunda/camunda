/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_ACTIVE;
import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_CANCELED;
import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_FILTER_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_GROUP_FILTERS;
import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_INCIDENTS;
import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.ProcessInstanceStatisticsAggregation.AGGREGATION_TO_FLOW_NODES;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_TYPE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.search.clients.aggregator.SearchChildrenAggregator;
import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.clients.aggregator.SearchFiltersAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.search.query.ProcessInstanceStatisticsQuery;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import org.junit.jupiter.api.Test;

public class ProcessInstanceFlowNodeStatisticsQueryTransformerTest {

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  protected <Q extends TypedSearchAggregationQuery> SearchQueryRequest transformQuery(
      final Q query) {
    return transformQuery(query, ResourceAccessChecks.disabled());
  }

  protected <Q extends TypedSearchAggregationQuery> SearchQueryRequest transformQuery(
      final Q query, final ResourceAccessChecks resourceAccessChecks) {
    return transformers
        .getTypedSearchQueryTransformer(query.getClass())
        .apply(query, resourceAccessChecks);
  }

  @Test
  public void shouldQueryByProcessInstanceKeyAndAggregation() {
    // given
    final var processInstanceKey = 123L;
    final var filter = new ProcessInstanceStatisticsFilter(processInstanceKey);
    final var statisticsQuery = new ProcessInstanceStatisticsQuery(filter);

    // when
    final var searchRequest = transformQuery(statisticsQuery);

    // then
    assertThat(searchRequest.sort()).isNull();
    assertThat(searchRequest.from()).isZero();
    assertThat(searchRequest.size()).isZero();

    final var aggregations = searchRequest.aggregations();
    assertThat(aggregations).hasSize(1);
    assertThat(aggregations.getFirst()).isInstanceOf(SearchChildrenAggregator.class);

    final var childrenAgg = (SearchChildrenAggregator) aggregations.getFirst();
    assertThat(childrenAgg.name()).isEqualTo(AGGREGATION_TO_FLOW_NODES);
    assertThat(childrenAgg.type()).isEqualTo(ListViewTemplate.ACTIVITIES_JOIN_RELATION);
    assertThat(childrenAgg.aggregations()).hasSize(1);
    assertThat(childrenAgg.aggregations().getFirst()).isInstanceOf(SearchFilterAggregator.class);

    final var filterAgg = (SearchFilterAggregator) childrenAgg.aggregations().getFirst();
    assertThat(filterAgg.name()).isEqualTo(AGGREGATION_FILTER_FLOW_NODES);
    assertThat(filterAgg.query())
        .isEqualTo(not(term(ACTIVITY_TYPE, FlowNodeType.MULTI_INSTANCE_BODY.toString())));
    assertThat(filterAgg.aggregations()).hasSize(1);
    assertThat(filterAgg.aggregations().getFirst()).isInstanceOf(SearchTermsAggregator.class);

    final var termsAgg = (SearchTermsAggregator) filterAgg.aggregations().getFirst();
    assertThat(termsAgg.name()).isEqualTo(AGGREGATION_GROUP_FLOW_NODES);
    assertThat(termsAgg.field()).isEqualTo(ListViewTemplate.ACTIVITY_ID);
    assertThat(termsAgg.size()).isEqualTo(AGGREGATION_TERMS_SIZE);
    assertThat(termsAgg.minDocCount()).isEqualTo(1);
    assertThat(termsAgg.aggregations()).hasSize(1);
    assertThat(termsAgg.aggregations().getFirst()).isInstanceOf(SearchFiltersAggregator.class);

    final var filtersAgg = (SearchFiltersAggregator) termsAgg.aggregations().getFirst();
    assertThat(filtersAgg.name()).isEqualTo(AGGREGATION_GROUP_FILTERS);
    assertThat(filtersAgg.aggregations()).isNull();
    assertThat(filtersAgg.queries()).hasSize(4);
    assertThat(filtersAgg.queries())
        .containsOnly(
            entry(
                AGGREGATION_ACTIVE,
                and(
                    term(ListViewTemplate.INCIDENT, false),
                    term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE.toString()))),
            entry(
                AGGREGATION_COMPLETED,
                term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.COMPLETED.toString())),
            entry(
                AGGREGATION_CANCELED,
                term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.TERMINATED.toString())),
            entry(
                AGGREGATION_INCIDENTS,
                and(
                    term(ListViewTemplate.INCIDENT, true),
                    term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE.toString()))));

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final var boolQuery = (SearchBoolQuery) queryVariant;
    assertThat(boolQuery.filter()).isEmpty();
    assertThat(boolQuery.should()).isEmpty();
    assertThat(boolQuery.mustNot()).isEmpty();
    assertThat(boolQuery.must())
        .containsExactly(
            term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            term(PROCESS_INSTANCE_KEY, filter.processInstanceKey()));
  }
}
