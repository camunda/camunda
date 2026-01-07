/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_AFFECTED_INSTANCES;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_BY_DEFINITION;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_SORT_AND_PAGE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_NAME_TOTAL_ESTIMATE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_SCRIPT_LANG;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.PROCESS_DEFINITION_AND_TENANT_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchBucketSortAggregator;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

final class IncidentProcessInstanceStatisticsByDefinitionAggregationTransformerTest {

  @Test
  void shouldBuildAggregationsForDefinitionStats() {
    // given
    final var transformers = ServiceTransformers.newInstance(new IndexDescriptors("", true));
    final var aggregationTransformer =
        transformers.getAggregationTransformer(
            IncidentProcessInstanceStatisticsByDefinitionAggregation.class);

    final var filter = FilterBuilders.incident().build();
    final var sort = SortOptionBuilders.incidentProcessInstanceStatisticsByDefinition().build();
    final var page = SearchQueryPage.of((p) -> p.from(5).size(10));

    final var aggregation =
        new IncidentProcessInstanceStatisticsByDefinitionAggregation(filter, sort, page);

    // when
    final List<SearchAggregator> aggregators =
        aggregationTransformer.apply(Tuple.of(aggregation, transformers));

    // then
    assertThat(aggregators).hasSize(2);

    assertThat(aggregators.get(0))
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            byDefinition -> {
              assertThat(byDefinition.getName()).isEqualTo(AGGREGATION_NAME_BY_DEFINITION);
              assertThat(byDefinition.lang()).isEqualTo(AGGREGATION_SCRIPT_LANG);
              assertThat(byDefinition.script()).isEqualTo(PROCESS_DEFINITION_AND_TENANT_KEY);
              assertThat(byDefinition.size()).isEqualTo(AGGREGATION_TERMS_SIZE);

              assertThat(byDefinition.getAggregations())
                  .extracting(SearchAggregator::getName)
                  .containsExactlyInAnyOrder(
                      AGGREGATION_NAME_AFFECTED_INSTANCES, AGGREGATION_NAME_SORT_AND_PAGE);

              assertThat(byDefinition.getAggregations())
                  .anySatisfy(
                      agg ->
                          assertThat(agg)
                              .isInstanceOfSatisfying(
                                  SearchBucketSortAggregator.class,
                                  b ->
                                      assertThat(b.getName())
                                          .isEqualTo(AGGREGATION_NAME_SORT_AND_PAGE)));
            });

    assertThat(aggregators.get(1))
        .isInstanceOfSatisfying(
            SearchCardinalityAggregator.class,
            totalEstimate -> {
              assertThat(totalEstimate.getName()).isEqualTo(AGGREGATION_NAME_TOTAL_ESTIMATE);
              assertThat(totalEstimate.lang()).isEqualTo(AGGREGATION_SCRIPT_LANG);
              assertThat(totalEstimate.script()).isEqualTo(PROCESS_DEFINITION_AND_TENANT_KEY);
            });
  }
}
