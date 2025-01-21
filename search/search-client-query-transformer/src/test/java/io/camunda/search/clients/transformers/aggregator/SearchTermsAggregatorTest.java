/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import org.junit.jupiter.api.Test;

public class SearchTermsAggregatorTest {

  @Test
  public void shouldBuildAggregatorWithValidValues() {
    // given
    final var aggregator =
        new SearchTermsAggregator.Builder()
            .name("A")
            .field("testField")
            .size(10)
            .minDocCount(1)
            .build();

    // then
    assertThat(aggregator.field()).isEqualTo("testField");
    assertThat(aggregator.size()).isEqualTo(10);
    assertThat(aggregator.minDocCount()).isEqualTo(1);
  }

  @Test
  public void shouldThrowExceptionWhenNameIsNull() {
    // when - then
    assertThatThrownBy(() -> new SearchTermsAggregator.Builder().build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Expected non-null name for terms aggregation.");
  }

  @Test
  public void shouldThrowExceptionWhenFieldIsNull() {
    // when - then
    assertThatThrownBy(
            () -> new SearchTermsAggregator.Builder().name("A").size(10).minDocCount(1).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Expected non-null field for terms aggregation.");
  }

  @Test
  public void shouldThrowExceptionWhenSizeIsInvalid() {
    // when - then
    assertThatThrownBy(
            () -> new SearchTermsAggregator.Builder().name("A").field("testField").size(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Size must be a positive integer.");
  }

  @Test
  public void shouldBuildWithDefaultSizeAndMinDocCount() {
    // given
    final var aggregator =
        new SearchTermsAggregator.Builder().name("A").field("testField").build(); // Use defaults

    // then
    assertThat(aggregator.size()).isEqualTo(10); // Default size
    assertThat(aggregator.minDocCount()).isEqualTo(1); // Default minDocCount
  }

  @Test
  public void shouldBuildWithCustomSizeAndMinDocCount() {
    // given
    final var aggregator =
        new SearchTermsAggregator.Builder()
            .name("A")
            .field("testField")
            .size(20)
            .minDocCount(5)
            .build();

    // then
    assertThat(aggregator.size()).isEqualTo(20);
    assertThat(aggregator.minDocCount()).isEqualTo(5);
  }
}
