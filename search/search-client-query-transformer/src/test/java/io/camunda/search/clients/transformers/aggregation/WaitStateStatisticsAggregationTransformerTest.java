/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.WaitStateStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.WaitStateTemplate;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaitStateStatisticsAggregationTransformerTest {

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  private final WaitStateStatisticsAggregationTransformer transformer =
      new WaitStateStatisticsAggregationTransformer();

  @Test
  void shouldBuildFlatTermsAggregationOnElementId() {
    // given
    final var aggregation = new WaitStateStatisticsAggregation();

    // when
    final List<SearchAggregator> aggregators =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    assertThat(aggregators).hasSize(1);
    assertThat(aggregators.getFirst())
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            terms -> {
              assertThat(terms.name())
                  .isEqualTo(WaitStateStatisticsAggregation.AGGREGATION_GROUP_ELEMENTS);
              assertThat(terms.field()).isEqualTo(WaitStateTemplate.ELEMENT_ID);
              assertThat(terms.size())
                  .isEqualTo(WaitStateStatisticsAggregation.AGGREGATION_TERMS_SIZE);
            });
  }
}
