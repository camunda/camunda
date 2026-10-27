/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static io.camunda.webapps.schema.descriptors.template.WaitStateTemplate.PROCESS_INSTANCE_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.WaitStateStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.WaitStateStatisticsFilter;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.query.WaitStateStatisticsQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import org.junit.jupiter.api.Test;

class WaitStateStatisticsQueryTransformerTest {

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  private <Q extends TypedSearchAggregationQuery> SearchQueryRequest transformQuery(final Q query) {
    return transformers.getTypedSearchQueryTransformer(query.getClass()).apply(query);
  }

  @Test
  void shouldQueryWaitStateIndexByProcessInstanceKeyWithTermsAggregation() {
    // given
    final var query = new WaitStateStatisticsQuery(new WaitStateStatisticsFilter(123L));

    // when
    final var request = transformQuery(query);

    // then
    assertThat(request.index()).anyMatch(i -> i.contains("wait-state"));
    assertThat(request.size()).isZero();
    assertThat(request.aggregations()).hasSize(1);
    assertThat(request.aggregations().getFirst())
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            terms ->
                assertThat(terms.name())
                    .isEqualTo(WaitStateStatisticsAggregation.AGGREGATION_GROUP_ELEMENTS));

    assertThat(request.query().queryOption())
        .isEqualTo(SearchQueryBuilders.term(PROCESS_INSTANCE_KEY, 123L).queryOption());
  }
}
