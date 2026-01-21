/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_ACTIVE;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_CANCELED;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_COMPLETED;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_INCIDENTS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FILTERS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODE_ID;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_PARENT_PI;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasParentQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchFiltersAggregator;
import io.camunda.search.clients.aggregator.SearchParentAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionFlowNodeStatisticsQueryTransformerTest {

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  protected <Q extends TypedSearchAggregationQuery> SearchQueryRequest transformQuery(
      final Q query) {
    return transformers.getTypedSearchQueryTransformer(query.getClass()).apply(query);
  }

  private void assertFiltersAggregation(final List<SearchAggregator> subAggregations) {
    assertThat(subAggregations).hasSize(1);

    final var filtersAgg = (SearchFiltersAggregator) subAggregations.getFirst();
    assertThat(filtersAgg.name()).isEqualTo(AGGREGATION_GROUP_FILTERS);

    // Verify all 4 filter queries are present
    final var queries = filtersAgg.queries();
    assertThat(queries).hasSize(4);

    assertThat(queries.get(AGGREGATION_FILTER_ACTIVE))
        .isEqualTo(
            and(
                term(ListViewTemplate.INCIDENT, false),
                term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())));

    assertThat(queries.get(AGGREGATION_FILTER_COMPLETED))
        .isEqualTo(term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.COMPLETED.toString()));

    assertThat(queries.get(AGGREGATION_FILTER_CANCELED))
        .isEqualTo(term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.TERMINATED.toString()));

    assertThat(queries.get(AGGREGATION_FILTER_INCIDENTS))
        .isEqualTo(
            and(
                term(ListViewTemplate.INCIDENT, true),
                term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())));

    // Verify parent aggregation is nested inside filters
    assertThat(filtersAgg.aggregations()).hasSize(1);
    final var parentAgg = (SearchParentAggregator) filtersAgg.aggregations().getFirst();
    assertThat(parentAgg.name()).isEqualTo(AGGREGATION_TO_PARENT_PI);
    assertThat(parentAgg.type()).isEqualTo(ListViewTemplate.ACTIVITIES_JOIN_RELATION);
    assertThat(parentAgg.aggregations()).isNull();
  }

  @Test
  public void shouldQueryByProcessDefinitionKeyAndAggregation() {
    // given
    final var processDefinitionKey = 123L;
    final var filter = new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey).build();
    final var statisticsQuery = new ProcessDefinitionFlowNodeStatisticsQuery(filter);

    // when
    final var searchRequest = transformQuery(statisticsQuery);

    // then
    assertThat(searchRequest.sort()).isNull();
    assertThat(searchRequest.from()).isZero();
    assertThat(searchRequest.size()).isZero();

    // Verify there is now a single top-level aggregation (terms by flow node ID)
    final var aggregations = searchRequest.aggregations();
    assertThat(aggregations).hasSize(1);

    assertThat(aggregations.getFirst())
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            termsAgg -> {
              assertThat(termsAgg.name()).isEqualTo(AGGREGATION_GROUP_FLOW_NODE_ID);
              assertThat(termsAgg.field()).isEqualTo(ListViewTemplate.ACTIVITY_ID);
              assertThat(termsAgg.size()).isEqualTo(AGGREGATION_TERMS_SIZE);
              assertThat(termsAgg.minDocCount()).isEqualTo(1);

              // Verify filters aggregation is nested inside terms
              assertFiltersAggregation(termsAgg.aggregations());
            });

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final var boolQuery = (SearchBoolQuery) queryVariant;
    assertThat(boolQuery.filter()).isEmpty();
    assertThat(boolQuery.should()).isEmpty();
    assertThat(boolQuery.mustNot()).isEmpty();
    assertThat(boolQuery.must())
        .containsExactly(
            term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.ACTIVITIES_JOIN_RELATION),
            hasParentQuery(
                ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION,
                term(ListViewTemplate.PROCESS_KEY, processDefinitionKey)));
  }
}
