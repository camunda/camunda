/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.VariableNameAggregation.AGGREGATION_NAME_BY_NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.VariableNameAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.SortOrder;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariableNameAggregationTransformerTest {

  private final VariableNameAggregationTransformer transformer =
      new VariableNameAggregationTransformer();

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  @Test
  void shouldBuildTermsAggregationOnNameFieldOrderedByKeyAscending() {
    // given
    final var filter = FilterBuilders.variable().build();
    final var page = SearchQueryPage.of(b -> b.size(25));
    final var aggregation = new VariableNameAggregation(filter, page);

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    assertThat(aggregations).hasSize(1);
    assertThat(aggregations.get(0))
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            termsAgg -> {
              assertThat(termsAgg.name()).isEqualTo(AGGREGATION_NAME_BY_NAME);
              assertThat(termsAgg.field()).isEqualTo(NAME);
              assertThat(termsAgg.size()).isEqualTo(25);
              assertThat(termsAgg.sorting()).hasSize(1);
              assertThat(termsAgg.sorting().get(0).field()).isEqualTo("_key");
              assertThat(termsAgg.sorting().get(0).order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  void shouldUseClientSuppliedLimitAsAggregationSize() {
    // given
    final var filter = FilterBuilders.variable().build();
    final var page = SearchQueryPage.of(b -> b.size(3));
    final var aggregation = new VariableNameAggregation(filter, page);

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var termsAgg = (SearchTermsAggregator) aggregations.get(0);
    assertThat(termsAgg.size()).isEqualTo(3);
  }
}
