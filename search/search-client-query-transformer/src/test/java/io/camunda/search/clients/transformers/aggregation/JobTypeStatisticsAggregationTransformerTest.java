/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_BY_TYPE;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_WORKERS;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.FIELD_COMPLETED_COUNT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.FIELD_CREATED_COUNT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.FIELD_FAILED_COUNT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.FIELD_JOB_TYPE;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.FIELD_LAST_COMPLETED_AT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.FIELD_LAST_CREATED_AT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.FIELD_LAST_FAILED_AT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.FIELD_WORKER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.JobTypeStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.clients.aggregator.SearchMaxAggregator;
import io.camunda.search.clients.aggregator.SearchSumAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobTypeStatisticsAggregationTransformerTest {

  private final JobTypeStatisticsAggregationTransformer transformer =
      new JobTypeStatisticsAggregationTransformer();

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  @Test
  void shouldBuildExpectedAggregationStructure() {
    // given
    final var aggregation = new JobTypeStatisticsAggregation();

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    assertThat(aggregations).hasSize(1);

    // Verify terms aggregation by job type
    assertThat(aggregations.get(0))
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            termsAgg -> {
              assertThat(termsAgg.name()).isEqualTo(AGGREGATION_BY_TYPE);
              assertThat(termsAgg.field()).isEqualTo(FIELD_JOB_TYPE);
              assertThat(termsAgg.aggregations()).hasSize(4);
            });
  }

  @Test
  void shouldBuildFilterBucketsForEachStatus() {
    // given
    final var aggregation = new JobTypeStatisticsAggregation();

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var termsAgg = (SearchTermsAggregator) aggregations.get(0);
    final var subAggregations = termsAgg.aggregations();

    // Verify created filter bucket
    assertThat(subAggregations.get(0))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            createdAgg -> {
              assertThat(createdAgg.name()).isEqualTo(AGGREGATION_CREATED);
              assertThat(createdAgg.query().queryOption()).isInstanceOf(SearchMatchAllQuery.class);
              assertSubAggregations(
                  createdAgg.aggregations(), FIELD_CREATED_COUNT, FIELD_LAST_CREATED_AT);
            });

    // Verify completed filter bucket
    assertThat(subAggregations.get(1))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            completedAgg -> {
              assertThat(completedAgg.name()).isEqualTo(AGGREGATION_COMPLETED);
              assertThat(completedAgg.query().queryOption())
                  .isInstanceOf(SearchMatchAllQuery.class);
              assertSubAggregations(
                  completedAgg.aggregations(), FIELD_COMPLETED_COUNT, FIELD_LAST_COMPLETED_AT);
            });

    // Verify failed filter bucket
    assertThat(subAggregations.get(2))
        .isInstanceOfSatisfying(
            SearchFilterAggregator.class,
            failedAgg -> {
              assertThat(failedAgg.name()).isEqualTo(AGGREGATION_FAILED);
              assertThat(failedAgg.query().queryOption()).isInstanceOf(SearchMatchAllQuery.class);
              assertSubAggregations(
                  failedAgg.aggregations(), FIELD_FAILED_COUNT, FIELD_LAST_FAILED_AT);
            });

    // Verify workers cardinality aggregation
    assertThat(subAggregations.get(3))
        .isInstanceOfSatisfying(
            SearchCardinalityAggregator.class,
            workersAgg -> {
              assertThat(workersAgg.name()).isEqualTo(AGGREGATION_WORKERS);
              assertThat(workersAgg.field()).isEqualTo(FIELD_WORKER);
            });
  }

  @Test
  void shouldBuildFilterBucketsWithMatchAllQuery() {
    // given
    final var aggregation = new JobTypeStatisticsAggregation();

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var termsAgg = (SearchTermsAggregator) aggregations.get(0);

    // All filter buckets should use match_all query
    termsAgg.aggregations().stream()
        .filter(SearchFilterAggregator.class::isInstance)
        .map(SearchFilterAggregator.class::cast)
        .forEach(
            filterAgg ->
                assertThat(filterAgg.query().queryOption())
                    .isInstanceOf(SearchMatchAllQuery.class));
  }

  @Test
  void shouldBuildSubAggregationsWithCorrectTypes() {
    // given
    final var aggregation = new JobTypeStatisticsAggregation();

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var termsAgg = (SearchTermsAggregator) aggregations.get(0);

    // Verify each filter bucket has exactly 2 sub-aggregations: sum and max
    termsAgg.aggregations().stream()
        .filter(SearchFilterAggregator.class::isInstance)
        .map(SearchFilterAggregator.class::cast)
        .forEach(
            filterAgg -> {
              assertThat(filterAgg.aggregations()).hasSize(2);

              // First sub-aggregation should be sum for count
              assertThat(filterAgg.aggregations().get(0))
                  .isInstanceOfSatisfying(
                      SearchSumAggregator.class,
                      sumAgg -> assertThat(sumAgg.name()).isEqualTo(AGGREGATION_COUNT));

              // Second sub-aggregation should be max for lastUpdatedAt
              assertThat(filterAgg.aggregations().get(1))
                  .isInstanceOfSatisfying(
                      SearchMaxAggregator.class,
                      maxAgg -> assertThat(maxAgg.name()).isEqualTo(AGGREGATION_LAST_UPDATED_AT));
            });
  }

  private void assertSubAggregations(
      final List<SearchAggregator> subAggs,
      final String expectedCountField,
      final String expectedLastUpdatedAtField) {
    assertThat(subAggs).hasSize(2);

    // Verify count sum aggregation
    assertThat(subAggs.get(0))
        .isInstanceOfSatisfying(
            SearchSumAggregator.class,
            sumAgg -> {
              assertThat(sumAgg.name()).isEqualTo(AGGREGATION_COUNT);
              assertThat(sumAgg.field()).isEqualTo(expectedCountField);
            });

    // Verify lastUpdatedAt max aggregation
    assertThat(subAggs.get(1))
        .isInstanceOfSatisfying(
            SearchMaxAggregator.class,
            maxAgg -> {
              assertThat(maxAgg.name()).isEqualTo(AGGREGATION_LAST_UPDATED_AT);
              assertThat(maxAgg.field()).isEqualTo(expectedLastUpdatedAtField);
            });
  }
}
