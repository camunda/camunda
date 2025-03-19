/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public class SearchTermsAggregatorTest {

  @Test
  public void shouldBuildAggregatorWithValidValues() {
    // given
    final var aggregator =
        SearchAggregatorBuilders.terms()
            .name("name")
            .field("testField")
            .size(10)
            .minDocCount(1)
            .aggregations(SearchAggregatorBuilders.terms("sub", "field"))
            .build();

    // then
    assertThat(aggregator.name()).isEqualTo("name");
    assertThat(aggregator.field()).isEqualTo("testField");
    assertThat(aggregator.size()).isEqualTo(10);
    assertThat(aggregator.minDocCount()).isEqualTo(1);
    assertThat(aggregator.aggregations())
        .containsExactly(SearchAggregatorBuilders.terms("sub", "field"));
  }

  @Test
  public void shouldThrowExceptionWhenNameIsNull() {
    // when - then
    assertThatThrownBy(() -> SearchAggregatorBuilders.terms().size(10).minDocCount(1).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Expected non-null field for name.");
  }

  @Test
  public void shouldThrowExceptionWhenFieldIsNull() {
    // when - then
    assertThatThrownBy(
            () -> SearchAggregatorBuilders.terms().name("name").size(10).minDocCount(1).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Expected non-null field for field.");
  }

  @Test
  public void shouldThrowExceptionWhenSizeIsInvalid() {
    // when - then
    assertThatThrownBy(
            () -> SearchAggregatorBuilders.terms().name("name").field("testField").size(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Size must be a positive integer.");
  }

  @Test
  public void shouldBuildWithDefaultSizeAndMinDocCount() {
    // given
    final var aggregator =
        SearchAggregatorBuilders.terms().name("name").field("testField").build(); // Use defaults

    // then
    assertThat(aggregator.name()).isEqualTo("name");
    assertThat(aggregator.size()).isEqualTo(10); // Default size
    assertThat(aggregator.minDocCount()).isEqualTo(1); // Default minDocCount
  }

  @Test
  public void shouldBuildWithCustomSizeAndMinDocCount() {
    // given
    final var aggregator =
        SearchAggregatorBuilders.terms()
            .name("name")
            .field("testField")
            .size(20)
            .minDocCount(5)
            .build();

    // then
    assertThat(aggregator.name()).isEqualTo("name");
    assertThat(aggregator.size()).isEqualTo(20);
    assertThat(aggregator.minDocCount()).isEqualTo(5);
  }
}
