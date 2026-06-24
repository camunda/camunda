/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_BY_ERROR;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_COMPOSITE_SIZE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_SOURCE_NAME_ERROR_CODE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_SOURCE_NAME_ERROR_MESSAGE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_WORKERS;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.FIELD_ERROR_CODE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.FIELD_ERROR_MESSAGE;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.FIELD_WORKER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.JobErrorStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.clients.aggregator.SearchCompositeAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobErrorStatisticsAggregationTransformerTest {

  private final JobErrorStatisticsAggregationTransformer transformer =
      new JobErrorStatisticsAggregationTransformer();

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  @Test
  void shouldBuildExpectedAggregationStructure() {
    // given
    final var page = SearchQueryPage.of(b -> b.size(50).after("cursor123"));
    final var aggregation = new JobErrorStatisticsAggregation(page);

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    assertThat(aggregations).hasSize(1);

    assertThat(aggregations.get(0))
        .isInstanceOfSatisfying(
            SearchCompositeAggregator.class,
            compositeAgg -> {
              assertThat(compositeAgg.name()).isEqualTo(AGGREGATION_BY_ERROR);
              assertThat(compositeAgg.size()).isEqualTo(50);
              assertThat(compositeAgg.after()).isEqualTo("cursor123");

              // Two terms sources: errorCode and errorMessage
              assertThat(compositeAgg.sources()).hasSize(2);
              assertThat(compositeAgg.sources().get(0))
                  .isInstanceOfSatisfying(
                      SearchTermsAggregator.class,
                      termsAgg -> {
                        assertThat(termsAgg.getName())
                            .isEqualTo(AGGREGATION_SOURCE_NAME_ERROR_CODE);
                        assertThat(termsAgg.field()).isEqualTo(FIELD_ERROR_CODE);
                      });
              assertThat(compositeAgg.sources().get(1))
                  .isInstanceOfSatisfying(
                      SearchTermsAggregator.class,
                      termsAgg -> {
                        assertThat(termsAgg.getName())
                            .isEqualTo(AGGREGATION_SOURCE_NAME_ERROR_MESSAGE);
                        assertThat(termsAgg.field()).isEqualTo(FIELD_ERROR_MESSAGE);
                      });

              // One cardinality sub-aggregation for workers
              assertThat(compositeAgg.aggregations()).hasSize(1);
              assertThat(compositeAgg.aggregations().get(0))
                  .isInstanceOfSatisfying(
                      SearchCardinalityAggregator.class,
                      cardAgg -> {
                        assertThat(cardAgg.getName()).isEqualTo(AGGREGATION_WORKERS);
                        assertThat(cardAgg.field()).isEqualTo(FIELD_WORKER);
                      });
            });
  }

  @Test
  void shouldUseDefaultSizeWhenPageIsNull() {
    // given
    final var aggregation = new JobErrorStatisticsAggregation(null);

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    assertThat(aggregations).hasSize(1);
    assertThat(aggregations.get(0))
        .isInstanceOfSatisfying(
            SearchCompositeAggregator.class,
            compositeAgg -> {
              assertThat(compositeAgg.size()).isEqualTo(AGGREGATION_COMPOSITE_SIZE);
              assertThat(compositeAgg.after()).isNull();
            });
  }
}
