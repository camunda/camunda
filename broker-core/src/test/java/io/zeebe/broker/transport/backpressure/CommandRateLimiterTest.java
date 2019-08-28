/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.limit.SettableLimit;
import java.util.stream.IntStream;
import org.junit.Test;

public class CommandRateLimiterTest {

  private static final int INITIAL_LIMIT = 5;
  private final SettableLimit limit = new SettableLimit(INITIAL_LIMIT);
  private final CommandRateLimiter rateLimiter = CommandRateLimiter.builder().limit(limit).build();

  @Test
  public void shouldAcquire() {
    assertThat(rateLimiter.tryAcquire(0, 1, null)).isTrue();
  }

  @Test
  public void shouldNotAcquireAfterLimit() {
    // given
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, 1, null)).isTrue());
    // then
    assertThat(rateLimiter.tryAcquire(0, 1, null)).isFalse();
  }

  @Test
  public void shouldCompleteRequestOnResponse() {
    // given
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, i, null)));
    assertThat(rateLimiter.tryAcquire(0, 100, null)).isFalse();

    // when
    rateLimiter.onResponse(0, 0);

    // then
    assertThat(rateLimiter.tryAcquire(0, 100, null)).isTrue();
  }

  @Test
  public void shouldCompleteAllRequests() {
    // given
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, i, null)));
    assertThat(rateLimiter.tryAcquire(0, 100, null)).isFalse();

    // when
    IntStream.range(0, limit.getLimit()).forEach(i -> rateLimiter.onResponse(0, i));

    // then
    IntStream.range(0, limit.getLimit())
        .forEach(i -> assertThat(rateLimiter.tryAcquire(0, i, null)).isTrue());
    assertThat(rateLimiter.tryAcquire(0, 100, null)).isFalse();
  }

  @Test
  public void shouldReleaseRequestOnIgnore() {
    // given
    rateLimiter.tryAcquire(0, 1, null);
    assertThat(rateLimiter.getInflightCount()).isEqualTo(1);

    // when
    rateLimiter.onIgnore(0, 1);

    // then
    assertThat(rateLimiter.getInflightCount()).isEqualTo(0);
  }
}
