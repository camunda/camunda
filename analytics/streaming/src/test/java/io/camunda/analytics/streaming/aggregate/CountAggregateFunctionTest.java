/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class CountAggregateFunctionTest {

  private final CountAggregateFunction<String> count = new CountAggregateFunction<>();

  @Test
  void shouldCountAndMerge() {
    // given — fold three facts into one accumulator, two into another
    final Long a = count.add("x", count.add("y", count.add("z", count.createAccumulator())));
    final Long b = count.add("p", count.add("q", count.createAccumulator()));

    // then
    assertThat(count.getResult(a)).isEqualTo(3L);
    assertThat(count.getResult(count.merge(a, b))).isEqualTo(5L);
  }
}
