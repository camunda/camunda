/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_ERROR_MESSAGE_SCRIPT;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_AFFECTED_INSTANCES;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_BY_ERROR;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_ERROR_HASH;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_SORT_AND_PAGE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_TOTAL_ESTIMATE;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_SCRIPT_LANG;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_TOTAL_ESTIMATE_SCRIPT;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.toBucketSortField;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchBucketSortAggregator;
import io.camunda.search.clients.aggregator.SearchCardinalityAggregator;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.IncidentProcessInstanceStatisticsByErrorSort;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentProcessInstanceStatisticsByErrorAggregationTransformerTest {

  private final IncidentProcessInstanceStatisticsByErrorAggregationTransformer transformer =
      new IncidentProcessInstanceStatisticsByErrorAggregationTransformer();

  private final ServiceTransformers transformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  @Test
  void shouldBuildExpectedAggregationTreeWithoutPaging() {
    // given
    final var aggregation =
        new IncidentProcessInstanceStatisticsByErrorAggregation(
            null,
            IncidentProcessInstanceStatisticsByErrorSort.of(s -> s.errorMessage().asc()),
            null);

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    assertThat(aggregations).hasSize(2);

    assertThat(aggregations.get(0))
        .isInstanceOfSatisfying(
            SearchTermsAggregator.class,
            byErrorAgg -> {
              assertThat(byErrorAgg.name()).isEqualTo(AGGREGATION_NAME_BY_ERROR);
              assertThat(byErrorAgg.script()).isEqualTo(AGGREGATION_ERROR_MESSAGE_SCRIPT);
              assertThat(byErrorAgg.lang()).isEqualTo(AGGREGATION_SCRIPT_LANG);

              final var subAggs = byErrorAgg.aggregations();
              assertThat(subAggs).hasSize(3);

              assertThat(subAggs.get(0))
                  .isInstanceOfSatisfying(
                      SearchTermsAggregator.class,
                      errorHashAgg -> {
                        assertThat(errorHashAgg.name()).isEqualTo(AGGREGATION_NAME_ERROR_HASH);
                        assertThat(errorHashAgg.field()).isEqualTo(IncidentTemplate.ERROR_MSG_HASH);
                        assertThat(errorHashAgg.size()).isEqualTo(1);
                      });

              assertThat(subAggs.get(1))
                  .isInstanceOfSatisfying(
                      SearchCardinalityAggregator.class,
                      affectedInstancesAgg -> {
                        assertThat(affectedInstancesAgg.name())
                            .isEqualTo(AGGREGATION_NAME_AFFECTED_INSTANCES);
                        assertThat(affectedInstancesAgg.field())
                            .isEqualTo(IncidentTemplate.PROCESS_INSTANCE_KEY);
                      });

              assertThat(subAggs.get(2))
                  .isInstanceOfSatisfying(
                      SearchBucketSortAggregator.class,
                      bucketSortAgg -> {
                        assertThat(bucketSortAgg.name()).isEqualTo(AGGREGATION_NAME_SORT_AND_PAGE);
                        // no paging information
                        assertThat(bucketSortAgg.from()).isNull();
                        assertThat(bucketSortAgg.size()).isNull();

                        assertThat(bucketSortAgg.sorting())
                            .containsExactly(
                                new FieldSorting(toBucketSortField("errorMessage"), SortOrder.ASC));
                      });
            });

    assertThat(aggregations.get(1))
        .isInstanceOfSatisfying(
            SearchCardinalityAggregator.class,
            totalEstimateAgg -> {
              assertThat(totalEstimateAgg.name()).isEqualTo(AGGREGATION_NAME_TOTAL_ESTIMATE);
              assertThat(totalEstimateAgg.script()).isEqualTo(AGGREGATION_TOTAL_ESTIMATE_SCRIPT);
              assertThat(totalEstimateAgg.lang()).isEqualTo(AGGREGATION_SCRIPT_LANG);
            });
  }

  @Test
  void shouldApplyBucketSortPagingAndSorting() {
    // given
    final var from = 10;
    final var size = 25;
    final var sort =
        IncidentProcessInstanceStatisticsByErrorSort.of(
            s -> s.activeInstancesWithErrorCount().desc().errorMessage().asc());

    final var aggregation =
        new IncidentProcessInstanceStatisticsByErrorAggregation(
            null,
            sort,
            new SearchQueryPage(
                from, size, null, null, SearchQueryPage.SearchQueryResultType.PAGINATED));

    // when
    final List<SearchAggregator> aggregations =
        transformer.apply(Tuple.of(aggregation, transformers));

    // then
    final var byErrorAgg = (SearchTermsAggregator) aggregations.getFirst();
    final List<SearchAggregator> subAggs = byErrorAgg.aggregations();

    final var bucketSortAgg = (SearchBucketSortAggregator) subAggs.get(2);
    assertThat(bucketSortAgg.name()).isEqualTo(AGGREGATION_NAME_SORT_AND_PAGE);
    assertThat(bucketSortAgg.from()).isEqualTo(from);
    assertThat(bucketSortAgg.size()).isEqualTo(size);

    assertThat(bucketSortAgg.sorting())
        .containsExactly(
            new FieldSorting(toBucketSortField("activeInstancesWithErrorCount"), SortOrder.DESC),
            new FieldSorting(toBucketSortField("errorMessage"), SortOrder.ASC));
  }
}
