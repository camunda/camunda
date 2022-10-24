/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StabilizingAIMDLimitTest {

  private final StabilizingAIMDLimit limit =
      StabilizingAIMDLimit.newBuilder()
          .initialLimit(10)
          .minLimit(1)
          .expectedRTT(1, TimeUnit.SECONDS)
          .build();

  @Test
  void shouldIncreaseLimitOnLowerRtt() {
    // when
    limit.onSample(0, TimeUnit.MILLISECONDS.toNanos(1), 9, false);
    // then
    assertThat(limit.getLimit()).isEqualTo(11);
  }

  @Test
  void shouldNotIncreaseLimitWhenLowInflight() {
    // when
    limit.onSample(0, TimeUnit.MILLISECONDS.toNanos(1), 2, false);
    // then
    assertThat(limit.getLimit()).isEqualTo(10);
  }

  @Test
  void shouldDecreaseLimitOnHigherRtt() {
    // when
    limit.onSample(0, TimeUnit.SECONDS.toNanos(2), 9, false);
    // then
    assertThat(limit.getLimit()).isEqualTo(9);
  }

  @Test
  void shouldNotDecreaseLimitWhenInflightGreaterThanLimit() {
    // when
    limit.onSample(0, TimeUnit.SECONDS.toNanos(2), 11, false);
    // then
    assertThat(limit.getLimit()).isEqualTo(10);
  }
}
