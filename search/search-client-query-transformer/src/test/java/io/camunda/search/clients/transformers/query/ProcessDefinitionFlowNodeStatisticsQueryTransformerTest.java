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
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_FLOW_NODES;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_FILTER_INCIDENTS;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_GROUP_FLOW_NODE_ID;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_CHILDREN_FN;
import static io.camunda.search.aggregation.ProcessDefinitionFlowNodeStatisticsAggregation.AGGREGATION_TO_PARENT_PI;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchChildrenAggregator;
import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.clients.aggregator.SearchParentAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
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

  private void assertSubAggregations(final List<SearchAggregator> subAggregations) {
    assertThat(subAggregations).hasSize(1);

    final var termsAgg = (SearchTermsAggregator) subAggregations.getFirst();
    assertThat(termsAgg.name()).isEqualTo(AGGREGATION_GROUP_FLOW_NODE_ID);
    assertThat(termsAgg.field()).isEqualTo(ListViewTemplate.ACTIVITY_ID);
    assertThat(termsAgg.size()).isEqualTo(AGGREGATION_TERMS_SIZE);
    assertThat(termsAgg.minDocCount()).isEqualTo(1);
    assertThat(termsAgg.aggregations()).hasSize(1);

    final var parentAgg = (SearchParentAggregator) termsAgg.aggregations().getFirst();
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

    final var aggregations = searchRequest.aggregations();
    assertThat(aggregations).hasSize(1);
    assertThat(aggregations.getFirst()).isInstanceOf(SearchChildrenAggregator.class);

    final var childrenAgg = (SearchChildrenAggregator) aggregations.getFirst();
    assertThat(childrenAgg.name()).isEqualTo(AGGREGATION_TO_CHILDREN_FN);
    assertThat(childrenAgg.type()).isEqualTo(ListViewTemplate.ACTIVITIES_JOIN_RELATION);
    assertThat(childrenAgg.aggregations()).hasSize(1);
    assertThat(childrenAgg.aggregations().getFirst()).isInstanceOf(SearchFilterAggregator.class);

    final var filterAgg = (SearchFilterAggregator) childrenAgg.aggregations().getFirst();
    assertThat(filterAgg.name()).isEqualTo(AGGREGATION_FILTER_FLOW_NODES);
    assertThat(filterAgg.query())
        .isEqualTo(
            or(
                not(term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.COMPLETED.toString())),
                term(ListViewTemplate.ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString())));
    assertThat(filterAgg.aggregations()).hasSize(4);

    assertThat(filterAgg.aggregations().get(0))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            activeFilter -> {
              assertThat(activeFilter.name()).isEqualTo(AGGREGATION_FILTER_ACTIVE);
              assertThat(activeFilter.query())
                  .isEqualTo(
                      and(
                          term(ListViewTemplate.INCIDENT, false),
                          term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())));
              assertSubAggregations(activeFilter.aggregations());
            });
    assertThat(filterAgg.aggregations().get(1))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            completedFilter -> {
              assertThat(completedFilter.name()).isEqualTo(AGGREGATION_FILTER_COMPLETED);
              assertThat(completedFilter.query())
                  .isEqualTo(
                      and(
                          term(ListViewTemplate.ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString()),
                          term(
                              ListViewTemplate.ACTIVITY_STATE,
                              FlowNodeState.COMPLETED.toString())));
              assertSubAggregations(completedFilter.aggregations());
            });
    assertThat(filterAgg.aggregations().get(2))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            canceledFilter -> {
              assertThat(canceledFilter.name()).isEqualTo(AGGREGATION_FILTER_CANCELED);
              assertThat(canceledFilter.query())
                  .isEqualTo(
                      term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.TERMINATED.toString()));
              assertSubAggregations(canceledFilter.aggregations());
            });
    assertThat(filterAgg.aggregations().get(3))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            incidentsFilter -> {
              assertThat(incidentsFilter.name()).isEqualTo(AGGREGATION_FILTER_INCIDENTS);
              assertThat(incidentsFilter.query())
                  .isEqualTo(
                      and(
                          term(ListViewTemplate.INCIDENT, true),
                          term(ListViewTemplate.ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())));
              assertSubAggregations(incidentsFilter.aggregations());
            });

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final var boolQuery = (SearchBoolQuery) queryVariant;
    assertThat(boolQuery.filter()).isEmpty();
    assertThat(boolQuery.should()).isEmpty();
    assertThat(boolQuery.mustNot()).isEmpty();
    assertThat(boolQuery.must())
        .containsExactly(
            SearchQueryBuilders.term(ListViewTemplate.PROCESS_KEY, processDefinitionKey),
            SearchQueryBuilders.term(
                ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION));
  }
}
