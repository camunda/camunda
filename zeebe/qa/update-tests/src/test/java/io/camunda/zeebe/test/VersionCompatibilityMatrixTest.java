/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.LinkedList;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VersionCompatibilityMatrixTest {
  @ParameterizedTest(name = "Sharding {0} elements into {1} shards")
  @MethodSource("sizeAndShards")
  void shouldShardCompletely(final int size, final int totalShards) {
    final var input = IntStream.range(0, size).boxed().toList();
    final var sharded = new LinkedList<Integer>();
    for (int i = 0; i < totalShards; i++) {
      final var shard = VersionCompatibilityMatrix.shard(input, i, totalShards).toList();
      assertThat(shard).isNotEmpty();
      sharded.addAll(shard);
    }
    assertThat(sharded).isEqualTo(input);
  }

  static Stream<Arguments> sizeAndShards() {
    return IntStream.rangeClosed(0, 10)
        .boxed()
        .flatMap(
            size ->
                IntStream.rangeClosed(1, 10)
                    .boxed()
                    .filter(shards -> shards < size)
                    .map(shards -> arguments(size, shards)));
  }
}
