/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_FIELD_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_BY_PROCESS_ID;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_LATEST_PROCESS_DEFINITION;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_PAGE;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_PROCESS_DEFINITION_KEY_CARDINALITY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITH_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_NAME_VERSION_COUNT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchBucketSortAggregator;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessDefinitionInstanceStatisticsSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.search.sort.SortOrder;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class ProcessDefinitionInstanceStatisticsAggregationTransformerTest {

  @Test
  void shouldBuildExpectedSubAggregations() {
    // given
    final var transformers = ServiceTransformers.newInstance(new IndexDescriptors("", true));
    final var aggregationTransformer =
        transformers.getAggregationTransformer(
            ProcessDefinitionInstanceStatisticsAggregation.class);

    final var filter = FilterBuilders.processInstance().build();
    final var sort = SortOptionBuilders.processDefinitionInstanceStatistics().build();
    final var page = SearchQueryPage.of(p -> p.from(0).size(10));

    final var aggregation = new ProcessDefinitionInstanceStatisticsAggregation(filter, sort, page);

    // when
    final List<SearchAggregator> aggregators =
        aggregationTransformer.apply(Tuple.of(aggregation, transformers));

    // then — two top-level aggregations: terms bucket and cardinality
    assertThat(aggregators).hasSize(2);

    assertThat(aggregators.get(0))
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            byProcessId -> {
              assertThat(byProcessId.getName()).isEqualTo(AGGREGATION_NAME_BY_PROCESS_ID);
              assertThat(byProcessId.size()).isEqualTo(AGGREGATION_TERMS_SIZE);

              assertThat(byProcessId.getAggregations())
                  .extracting(SearchAggregator::getName)
                  .containsExactlyInAnyOrder(
                      AGGREGATION_NAME_LATEST_PROCESS_DEFINITION,
                      AGGREGATION_NAME_VERSION_COUNT,
                      AGGREGATION_NAME_TOTAL_WITH_INCIDENT,
                      AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT,
                      AGGREGATION_NAME_PAGE);
            });

    assertThat(aggregators.get(1))
        .isInstanceOfSatisfying(
            SearchCardinalityAggregator.class,
            cardinality ->
                assertThat(cardinality.getName())
                    .isEqualTo(AGGREGATION_NAME_PROCESS_DEFINITION_KEY_CARDINALITY));
  }

  private static Stream<Arguments> sortFieldToBucketSortPath() {
    return Stream.of(
        Arguments.of(AGGREGATION_FIELD_KEY, AGGREGATION_FIELD_KEY),
        Arguments.of(
            AGGREGATION_NAME_TOTAL_WITH_INCIDENT, AGGREGATION_NAME_TOTAL_WITH_INCIDENT + "._count"),
        Arguments.of(
            AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT,
            AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT + "._count"));
  }

  @ParameterizedTest(name = "sort field {0} → bucket_sort path {1}")
  @MethodSource("sortFieldToBucketSortPath")
  void shouldMapSortFieldToBucketSortPath(
      final String sortField, final String expectedBucketSortPath) {
    // given
    final var transformers = ServiceTransformers.newInstance(new IndexDescriptors("", true));
    final var aggregationTransformer =
        transformers.getAggregationTransformer(
            ProcessDefinitionInstanceStatisticsAggregation.class);

    final var filter = FilterBuilders.processInstance().build();
    final var sort =
        new ProcessDefinitionInstanceStatisticsSort(
            List.of(new FieldSorting(sortField, SortOrder.ASC)));
    final var page = SearchQueryPage.of(p -> p.from(0).size(10));

    final var aggregation = new ProcessDefinitionInstanceStatisticsAggregation(filter, sort, page);

    // when
    final List<SearchAggregator> aggregators =
        aggregationTransformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var bucketSortAgg =
        aggregators.get(0).getAggregations().stream()
            .filter(a -> AGGREGATION_NAME_PAGE.equals(a.getName()))
            .findFirst()
            .orElseThrow();

    assertThat(bucketSortAgg)
        .isInstanceOfSatisfying(
            SearchBucketSortAggregator.class,
            bucketSort ->
                assertThat(bucketSort.sorting())
                    .containsExactly(new FieldSorting(expectedBucketSortPath, SortOrder.ASC)));
  }

  @Test
  void shouldThrowOnUnsupportedSortField() {
    // given
    final var transformers = ServiceTransformers.newInstance(new IndexDescriptors("", true));
    final var aggregationTransformer =
        transformers.getAggregationTransformer(
            ProcessDefinitionInstanceStatisticsAggregation.class);

    final var filter = FilterBuilders.processInstance().build();
    final var sort =
        new ProcessDefinitionInstanceStatisticsSort(
            List.of(new FieldSorting("unsupportedField", SortOrder.ASC)));
    final var page = SearchQueryPage.of(p -> p.from(0).size(10));

    final var aggregation = new ProcessDefinitionInstanceStatisticsAggregation(filter, sort, page);

    // when / then
    assertThatThrownBy(() -> aggregationTransformer.apply(Tuple.of(aggregation, transformers)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported sort field for statistics bucket_sort: unsupportedField");
  }
}
