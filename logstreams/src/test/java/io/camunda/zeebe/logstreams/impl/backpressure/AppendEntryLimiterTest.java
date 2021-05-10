/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.limit.SettableLimit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class AppendEntryLimiterTest {

  @Parameter public Limit limit;
  private AppendLimiter limiter;

  @Parameters(name = "{index}: {0}")
  public static Object[][] limits() {
    return new Object[][] {
      {new SettableLimit(1)},
      {new AppenderVegasCfg().setInitialLimit(1).setMaxConcurrency(1).get()},
      {new AppenderGradient2Cfg().setInitialLimit(1).setMinLimit(1).setMaxConcurrency(1).get()}
    };
  }

  @Before
  public void init() {
    limiter = AppendEntryLimiter.builder().limit(limit).build();
  }

  @Test
  public void shouldAcquire() {
    // given

    // when
    final boolean acquired = limiter.tryAcquire(1024L);

    // then
    assertThat(acquired).isTrue();
    assertThat(limiter.getInflight()).isEqualTo(1);
    assertThat(limiter.getLimit()).isEqualTo(1);
  }

  @Test
  public void shouldNotAcquireMore() {
    // given
    limiter.tryAcquire(1024L);

    // when
    final boolean acquired = limiter.tryAcquire(1024L);

    // then
    assertThat(acquired).isFalse();
    assertThat(limiter.getInflight()).isEqualTo(1);
    assertThat(limiter.getLimit()).isEqualTo(1);
  }

  @Test
  public void shouldReclaim() {
    // given
    limiter.tryAcquire(1024L);

    // when
    limiter.onCommit(1024L);

    // then
    assertThat(limiter.getInflight()).isEqualTo(0);
    assertThat(limiter.getLimit()).isEqualTo(1);
  }
}
