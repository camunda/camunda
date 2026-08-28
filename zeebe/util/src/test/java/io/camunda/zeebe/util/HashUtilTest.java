/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class HashUtilTest {

  /**
   * Taken from Elasticsearch 8.19.16 and OpenSearch 2.19.5, which were asked through their
   * _search_shards API which shard each routing value belongs to, and agreed on every value. The
   * expectations only hold as long as {@link HashUtil#getShardForRouting(String, int)} computes
   * what those engines do: a routing value that lands elsewhere sends a document to a shard the
   * caller did not intend.
   */
  @ParameterizedTest(name = "routing {0} of {1} shards is on shard {2}")
  @CsvSource({
    // The bare partition ids 1, 2 and 3 pile onto one shard, which is what balanced routing
    // values exist to avoid
    "1, 2, 0",
    "2, 2, 0",
    "3, 2, 0",
    "1, 3, 2",
    "2, 3, 1",
    "3, 3, 1",
    "1, 5, 4",
    "2, 5, 3",
    "3, 5, 0",
    "1, 8, 0",
    "2, 8, 0",
    "3, 8, 1",
    // The balanced values the exporters derive for three shards, one partition per shard
    "1#3, 3, 0",
    "2#3, 3, 1",
    "3#1, 3, 2",
  })
  void shouldComputeTheShardTheSearchEnginesUse(
      final String routing, final int numberOfShards, final int expectedShard) {
    // when
    final int shard = HashUtil.getShardForRouting(routing, numberOfShards);

    // then
    assertThat(shard).isEqualTo(expectedShard);
  }

  @Test
  void shouldKeepTheShardWithinTheNumberOfShards() {
    // when
    final var shards =
        IntStream.rangeClosed(1, 1000)
            .map(value -> HashUtil.getShardForRouting(String.valueOf(value), 3))
            .boxed()
            .toList();

    // then
    assertThat(shards).allMatch(shard -> shard >= 0 && shard < 3);
  }

  @Test
  void shouldReturnTheOnlyShardOfASingleShardIndex() {
    // when
    final int shard = HashUtil.getShardForRouting("anything", 1);

    // then
    assertThat(shard).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void shouldRejectANonPositiveNumberOfShards(final int numberOfShards) {
    // when / then
    assertThatThrownBy(() -> HashUtil.getShardForRouting("1", numberOfShards))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 1");
  }
}
