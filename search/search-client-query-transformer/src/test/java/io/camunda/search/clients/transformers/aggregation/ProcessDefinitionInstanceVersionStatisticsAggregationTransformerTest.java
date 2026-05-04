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
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

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
              // Note: maxProcessName is absent — processName is a keyword field that max() does not
              // support in ES/OS; name sorting is handled in Java after fetching results.
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

  @Test
  void shouldMapAllSortFieldsToCorrectBucketSortPaths() {
    // given
    final var transformers = ServiceTransformers.newInstance(new IndexDescriptors("", true));
    final var aggregationTransformer =
        transformers.getAggregationTransformer(
            ProcessDefinitionInstanceVersionStatisticsAggregation.class);

    final var filter = new ProcessDefinitionInstanceVersionStatisticsFilter("order_process", null);
    // Note: the document reader converts "processDefinitionId" → "_key" via
    // withConvertedSortingField before the transformer is called, so we use "_key" directly here.
    final ProcessDefinitionInstanceVersionStatisticsSort sort =
        new ProcessDefinitionInstanceVersionStatisticsSort(
            List.of(
                new FieldSorting(AGGREGATION_FIELD_KEY, io.camunda.search.sort.SortOrder.ASC),
                new FieldSorting(
                    AGGREGATION_FIELD_PROCESS_DEFINITION_KEY, io.camunda.search.sort.SortOrder.ASC),
                new FieldSorting(
                    AGGREGATION_FIELD_PROCESS_DEFINITION_NAME,
                    io.camunda.search.sort.SortOrder.ASC),
                new FieldSorting(
                    AGGREGATION_NAME_PROCESS_DEFINITION_VERSION,
                    io.camunda.search.sort.SortOrder.ASC),
                new FieldSorting(
                    AGGREGATION_NAME_TOTAL_WITH_INCIDENT, io.camunda.search.sort.SortOrder.DESC),
                new FieldSorting(
                    AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT,
                    io.camunda.search.sort.SortOrder.DESC)));
    final var page = SearchQueryPage.of(p -> p.from(0).size(10));

    final var aggregation =
        new ProcessDefinitionInstanceVersionStatisticsAggregation(filter, sort, page);

    // when
    final List<SearchAggregator> aggregators =
        aggregationTransformer.apply(Tuple.of(aggregation, transformers));

    // then — inspect the bucket_sort sub-aggregation for correct path mapping
    final var bucketSortAgg =
        aggregators.get(1).getAggregations().stream()
            .filter(a -> AGGREGATION_NAME_PAGE.equals(a.getName()))
            .findFirst()
            .orElseThrow();

    assertThat(bucketSortAgg)
        .isInstanceOfSatisfying(
            SearchBucketSortAggregator.class,
            bucketSort -> {
              final var sortings = bucketSort.sorting();
              // processDefinitionName is excluded from bucket_sort (keyword field; handled in Java)
              assertThat(sortings).hasSize(5);

              // _key (processDefinitionId after conversion) maps to the bucket key directly
              assertThat(sortings.get(0))
                  .isEqualTo(
                      new FieldSorting(
                          AGGREGATION_FIELD_KEY, io.camunda.search.sort.SortOrder.ASC));

              // processDefinitionKey maps to maxProcessDefinitionKey.value
              assertThat(sortings.get(1))
                  .isEqualTo(
                      new FieldSorting(
                          AGG_MAX_PROCESS_DEFINITION_KEY + ".value",
                          io.camunda.search.sort.SortOrder.ASC));

              // processDefinitionVersion maps to maxProcessVersion.value
              assertThat(sortings.get(2))
                  .isEqualTo(
                      new FieldSorting(
                          AGG_MAX_PROCESS_VERSION + ".value",
                          io.camunda.search.sort.SortOrder.ASC));

              // activeInstancesWithIncidentCount maps to ._count on the filter sub-agg
              assertThat(sortings.get(3))
                  .isEqualTo(
                      new FieldSorting(
                          AGGREGATION_NAME_TOTAL_WITH_INCIDENT + "._count",
                          io.camunda.search.sort.SortOrder.DESC));

              // activeInstancesWithoutIncidentCount maps to ._count on the filter sub-agg
              assertThat(sortings.get(4))
                  .isEqualTo(
                      new FieldSorting(
                          AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT + "._count",
                          io.camunda.search.sort.SortOrder.DESC));
            });
  }
}
