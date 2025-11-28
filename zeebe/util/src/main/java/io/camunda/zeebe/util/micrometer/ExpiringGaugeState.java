/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;
import org.apache.commons.lang3.NotImplementedException;

class ExpiringGaugeState implements GaugeState {
  private final Clock clock;
  private final long expirationMillis;
  private final long expiredValue;
  private volatile long lastValueSetMillis;
  private final AtomicLong value = new AtomicLong();

  ExpiringGaugeState(final Duration expiration, final long expiredValue) {
    this(Clock.systemUTC(), expiration, expiredValue);
  }

  @VisibleForTesting
  ExpiringGaugeState(final Clock clock, final Duration expiration, final long expiredValue) {
    this.clock = clock;
    expirationMillis = expiration.toMillis();
    this.expiredValue = expiredValue;
  }

  @Override
  public void set(final long value) {
    this.value.set(value);
    lastValueSetMillis = clock.millis();
  }

  @Override
  public long get() {
    if ((clock.millis() - expirationMillis) > lastValueSetMillis) {
      return expiredValue;
    }
    return value.get();
  }

  @Override
  public long updateAndGet(final LongUnaryOperator updateFunction) {
    throw new NotImplementedException("not implemented");
  }
}
