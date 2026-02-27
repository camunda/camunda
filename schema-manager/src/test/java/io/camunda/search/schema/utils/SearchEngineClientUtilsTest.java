/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import static io.camunda.search.schema.utils.SearchEngineClientUtils.MAX_INDEX_PATTERN_REQUEST_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SearchEngineClientUtilsTest {

  @Test
  void shouldReturnSingleBatchWhenPatternWithinLimit() {
    // given
    final var namePattern = "index-a*,index-b*,index-c*";

    // when
    final var batches = SearchEngineClientUtils.batchPatterns(namePattern);

    // then
    assertThat(batches).hasSize(1);
    assertThat(batches.get(0)).isEqualTo(namePattern);
  }

  @Test
  void shouldReturnEmptyListWhenNullPatternGiven() {
    // when
    final var batches = SearchEngineClientUtils.batchPatterns(null);

    // then
    assertThat(batches).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenEmptyPatternGiven() {
    // when
    final var batches = SearchEngineClientUtils.batchPatterns("");

    // then
    assertThat(batches).isEmpty();
  }

  @Test
  void shouldSplitIntoBatchesWhenPatternExceedsMaxLength() {
    // given – create patterns that together exceed the max request length
    final var longPart = "a".repeat(2000);
    final var namePattern = longPart + "*," + longPart + "*," + longPart + "*";

    // when
    final var batches = SearchEngineClientUtils.batchPatterns(namePattern);

    // then
    assertThat(batches).hasSizeGreaterThan(1);
    // Each batch must not exceed the configured max length
    batches.forEach(
        batch -> assertThat(batch.length()).isLessThanOrEqualTo(MAX_INDEX_PATTERN_REQUEST_LENGTH));
    // All patterns must appear across batches
    final var allPatterns = batches.stream().flatMap(b -> Stream.of(b.split(","))).toList();
    assertThat(allPatterns).containsExactlyInAnyOrderElementsOf(List.of(namePattern.split(",")));
  }

  @Test
  void shouldPutOversizedSinglePatternInItsOwnBatch() {
    // given – a single pattern that is itself longer than the limit
    final var largePattern = "x".repeat(MAX_INDEX_PATTERN_REQUEST_LENGTH) + "*";

    // when
    final var batches = SearchEngineClientUtils.batchPatterns(largePattern);

    // then
    assertThat(batches).hasSize(1);
    assertThat(batches.get(0)).isEqualTo(largePattern);
  }
}
