/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_FIELD_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_FIELD_PROCESS_DEFINITION_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_FIELD_PROCESS_DEFINITION_NAME;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_BY_VERSION_TENANT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_PAGE;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_PROCESS_DEFINITION_VERSION;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_TOTAL_WITH_INCIDENT;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_NAME_VERSION_CARDINALITY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGG_MAX_PROCESS_DEFINITION_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGG_MAX_PROCESS_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchBucketSortAggregator;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.clients.aggregator.SearchMaxAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.ProcessDefinitionInstanceVersionStatisticsSort;
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

final class ProcessDefinitionInstanceVersionStatisticsAggregationTransformerTest {

  @Test
  void shouldBuildAggregationsWithMaxSubAggregations() {
    // given
    final var transformers = ServiceTransformers.newInstance(new IndexDescriptors("", true));
    final var aggregationTransformer =
        transformers.getAggregationTransformer(
            ProcessDefinitionInstanceVersionStatisticsAggregation.class);

    final var filter = new ProcessDefinitionInstanceVersionStatisticsFilter("order_process", null);
    final var sort = SortOptionBuilders.processDefinitionInstanceVersionStatistics().build();
    final var page = SearchQueryPage.of(p -> p.from(0).size(10));

    final var aggregation =
        new ProcessDefinitionInstanceVersionStatisticsAggregation(filter, sort, page);

    // when
    final List<SearchAggregator> aggregators =
        aggregationTransformer.apply(Tuple.of(aggregation, transformers));

    // then — two top-level aggregations: cardinality and the terms bucket
    assertThat(aggregators).hasSize(2);

    assertThat(aggregators.get(0))
        .isInstanceOfSatisfying(
            SearchCardinalityAggregator.class,
            cardinality ->
                assertThat(cardinality.getName()).isEqualTo(AGGREGATION_NAME_VERSION_CARDINALITY));

    assertThat(aggregators.get(1))
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            byVersionTenant -> {
              assertThat(byVersionTenant.getName()).isEqualTo(AGGREGATION_NAME_BY_VERSION_TENANT);
              assertThat(byVersionTenant.size()).isEqualTo(AGGREGATION_TERMS_SIZE);

              // Verify all expected sub-aggregation names are present
              assertThat(byVersionTenant.getAggregations())
                  .extracting(SearchAggregator::getName)
                  .containsExactlyInAnyOrder(
                      AGGREGATION_NAME_PROCESS_DEFINITION_VERSION,
                      AGGREGATION_NAME_TOTAL_WITH_INCIDENT,
                      AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT,
                      AGG_MAX_PROCESS_DEFINITION_KEY,
                      AGG_MAX_PROCESS_VERSION,
                      AGGREGATION_NAME_PAGE);

              // Verify the two max sub-aggregations (numeric fields only)
              assertThat(byVersionTenant.getAggregations())
                  .filteredOn(a -> a instanceof SearchMaxAggregator)
                  .extracting(SearchAggregator::getName)
                  .containsExactlyInAnyOrder(
                      AGG_MAX_PROCESS_DEFINITION_KEY, AGG_MAX_PROCESS_VERSION);
            });
  }

  private static Stream<Arguments> sortFieldToBucketSortPath() {
    // Note: the document reader converts "processDefinitionId" → "_key" via
    // withConvertedSortingField before the transformer is called, so we use "_key" directly here.
    return Stream.of(
        Arguments.of(AGGREGATION_FIELD_KEY, AGGREGATION_FIELD_KEY),
        Arguments.of(
            AGGREGATION_FIELD_PROCESS_DEFINITION_KEY, AGG_MAX_PROCESS_DEFINITION_KEY + ".value"),
        // processDefinitionName is embedded as the first segment of the terms bucket key,
        // so sorting by it is equivalent to sorting by _key.
        Arguments.of(AGGREGATION_FIELD_PROCESS_DEFINITION_NAME, AGGREGATION_FIELD_KEY),
        Arguments.of(
            AGGREGATION_NAME_PROCESS_DEFINITION_VERSION, AGG_MAX_PROCESS_VERSION + ".value"),
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
            ProcessDefinitionInstanceVersionStatisticsAggregation.class);

    final var filter = new ProcessDefinitionInstanceVersionStatisticsFilter("order_process", null);
    final var sort =
        new ProcessDefinitionInstanceVersionStatisticsSort(
            List.of(new FieldSorting(sortField, SortOrder.ASC)));
    final var page = SearchQueryPage.of(p -> p.from(0).size(10));

    final var aggregation =
        new ProcessDefinitionInstanceVersionStatisticsAggregation(filter, sort, page);

    // when
    final List<SearchAggregator> aggregators =
        aggregationTransformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var bucketSortAgg =
        aggregators.get(1).getAggregations().stream()
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
            ProcessDefinitionInstanceVersionStatisticsAggregation.class);

    final var filter = new ProcessDefinitionInstanceVersionStatisticsFilter("order_process", null);
    final var sort =
        new ProcessDefinitionInstanceVersionStatisticsSort(
            List.of(new FieldSorting("unsupportedField", SortOrder.ASC)));
    final var page = SearchQueryPage.of(p -> p.from(0).size(10));

    final var aggregation =
        new ProcessDefinitionInstanceVersionStatisticsAggregation(filter, sort, page);

    // when / then
    assertThatThrownBy(() -> aggregationTransformer.apply(Tuple.of(aggregation, transformers)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Unsupported sort field for version-statistics bucket_sort mapping: unsupportedField");
  }
}
