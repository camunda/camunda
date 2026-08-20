/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.search.clients.aggregator.SearchAggregatorBuilders;
import io.camunda.search.clients.aggregator.SearchBucketSortAggregator;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SearchBucketSortAggregationTransformerTest {

  private SearchBucketSortAggregationTransformer transformer;

  @BeforeEach
  public void setUp() {
    transformer = new SearchBucketSortAggregationTransformer(new ElasticsearchTransformers());
  }

  @Test
  public void shouldTransformWithSortingFromAndSize() {
    final SearchBucketSortAggregator aggregator =
        SearchAggregatorBuilders.bucketSort()
            .name("foo")
            .sorting(List.of(new FieldSorting("name", SortOrder.ASC)))
            .from(5)
            .size(10)
            .build();

    final Aggregation aggregation = transformer.apply(aggregator);
    assertThat(aggregation.bucketSort()).isNotNull();
    assertThat(aggregation.bucketSort().sort()).hasSize(1);
    assertThat(aggregation.bucketSort().from()).isEqualTo(5);
    assertThat(aggregation.bucketSort().size()).isEqualTo(10);
    assertThat(aggregation.bucketSort().sort().getFirst().field().field()).isEqualTo("name");
    assertThat(
            Objects.requireNonNull(aggregation.bucketSort().sort().getFirst().field().order())
                .jsonValue())
        .isEqualTo("asc");
  }

  @Test
  public void shouldTransformWithNoSorting() {
    final SearchBucketSortAggregator aggregator =
        SearchAggregatorBuilders.bucketSort().name("foo").from(0).size(20).build();

    final Aggregation aggregation = transformer.apply(aggregator);
    assertThat(aggregation.bucketSort()).isNotNull();
    assertThat(aggregation.bucketSort().sort()).isEmpty();
    assertThat(aggregation.bucketSort().from()).isEqualTo(0);
    assertThat(aggregation.bucketSort().size()).isEqualTo(20);
  }

  @Test
  public void shouldTransformWithMultipleSortFields() {
    final SearchBucketSortAggregator aggregator =
        SearchAggregatorBuilders.bucketSort()
            .name("foo")
            .sorting(
                List.of(
                    new FieldSorting("name", SortOrder.ASC),
                    new FieldSorting("date", SortOrder.DESC)))
            .size(15)
            .build();

    final Aggregation aggregation = transformer.apply(aggregator);
    assertThat(aggregation.bucketSort()).isNotNull();
    assertThat(aggregation.bucketSort().sort()).hasSize(2);
    assertThat(aggregation.bucketSort().sort().get(0).field().field()).isEqualTo("name");
    assertThat(
            Objects.requireNonNull(aggregation.bucketSort().sort().get(0).field().order())
                .jsonValue())
        .isEqualTo("asc");
    assertThat(aggregation.bucketSort().sort().get(1).field().field()).isEqualTo("date");
    assertThat(
            Objects.requireNonNull(aggregation.bucketSort().sort().get(1).field().order())
                .jsonValue())
        .isEqualTo("desc");
    assertThat(aggregation.bucketSort().size()).isEqualTo(15);
  }

  @Test
  public void shouldThrowErrorOnInvalidSize() {
    assertThatThrownBy(
            () ->
                SearchAggregatorBuilders.bucketSort()
                    .name("foo")
                    .sorting(List.of())
                    .size(-1)
                    .build())
        .hasMessageContaining("Size must be greater than or equal to 0.")
        .isInstanceOf(IllegalArgumentException.class);
  }
}
