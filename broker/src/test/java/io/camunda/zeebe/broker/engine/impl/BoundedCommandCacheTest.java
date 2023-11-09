/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.agrona.collections.LongHashSet;
import org.junit.jupiter.api.Test;

final class BoundedCommandCacheTest {
  @Test
  void shouldNotExceedCapacity() {
    // given
    final var cache = new BoundedCommandCache(4);
    cache.add(setOf(1, 2, 3, 4));

    // when
    cache.add(setOf(5, 6));

    // then
    assertThat(cache.size()).isEqualTo(4);
    assertThat(cache.contains(5)).isTrue();
    assertThat(cache.contains(6)).isTrue();
  }

  private LongHashSet setOf(final long... keys) {
    final var set = new LongHashSet();
    set.addAll(Arrays.stream(keys).boxed().toList());
    return set;
  }
}
