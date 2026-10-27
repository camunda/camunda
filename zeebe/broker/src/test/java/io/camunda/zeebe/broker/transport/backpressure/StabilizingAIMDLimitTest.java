/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.impl.flowcontrol.StabilizingAIMDLimit;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StabilizingAIMDLimitTest {

  private StabilizingAIMDLimit limit;

  @BeforeEach
  public void setup() {
    limit =
        StabilizingAIMDLimit.newBuilder()
            .initialLimit(10)
            .minLimit(5)
            .maxLimit(100)
            .expectedRTT(1, TimeUnit.SECONDS)
            .build();
  }

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

  @Test
  void shouldNotIncreaseLimitMoreThanMax() {
    for (int i = 0; i < 10000; i++) {
      limit.onSample(i, TimeUnit.MILLISECONDS.toNanos(1), Math.max(i, 10), false);
    }
    assertThat(limit.getLimit()).isEqualTo(100);
  }

  @Test
  void shouldNotDecreaseLimitBelowMin() {
    for (int i = 0; i < 1000; i++) {
      limit.onSample(0, TimeUnit.SECONDS.toNanos(10), 1, true);
    }
    assertThat(limit.getLimit()).isEqualTo(5);
  }
}
