/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

import io.camunda.zeebe.util.EnsureUtil;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "zeebe.actor.idle")
public final class IdleStrategyConfig {
  private static final long DEFAULT_MAX_SPINS = 100;
  private static final long DEFAULT_MAX_YIELDS = 100;
  private static final Duration DEFAULT_MAX_PARK_PERIOD = Duration.ofNanos(1);
  private static final Duration DEFAULT_MIN_PARK_PERIOD = Duration.ofMillis(1);

  private long maxSpins = DEFAULT_MAX_SPINS;
  private long maxYields = DEFAULT_MAX_YIELDS;
  private Duration minParkPeriod = DEFAULT_MAX_PARK_PERIOD;
  private Duration maxParkPeriod = DEFAULT_MAX_PARK_PERIOD;

  public long getMaxSpins() {
    return maxSpins;
  }

  public IdleStrategyConfig setMaxSpins(final long maxSpins) {
    this.maxSpins = EnsureUtil.ensureGreaterThan("maxSpins", maxSpins, 0);
    return this;
  }

  public long getMaxYields() {
    return maxYields;
  }

  public IdleStrategyConfig setMaxYields(final long maxYields) {
    this.maxYields = EnsureUtil.ensureGreaterThan("maxYields", maxYields, 0);
    return this;
  }

  public Duration getMinParkPeriod() {
    return minParkPeriod;
  }

  public IdleStrategyConfig setMinParkPeriod(final Duration minParkPeriod) {
    this.minParkPeriod = minParkPeriod != null ? minParkPeriod : DEFAULT_MIN_PARK_PERIOD;
    return this;
  }

  public Duration getMaxParkPeriod() {
    return maxParkPeriod;
  }

  public IdleStrategyConfig setMaxParkPeriod(final Duration maxParkPeriod) {
    this.maxParkPeriod = maxParkPeriod != null ? maxParkPeriod : DEFAULT_MAX_PARK_PERIOD;
    return this;
  }

  public Supplier<IdleStrategy> toSupplier() {
    return new IdleStrategySupplier(this);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxSpins, maxYields, minParkPeriod, maxParkPeriod);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final IdleStrategyConfig that = (IdleStrategyConfig) o;
    return maxSpins == that.maxSpins
        && maxYields == that.maxYields
        && Objects.equals(minParkPeriod, that.minParkPeriod)
        && Objects.equals(maxParkPeriod, that.maxParkPeriod);
  }

  @Override
  public String toString() {
    return "IdleStrategyConfig{"
        + "maxSpins="
        + maxSpins
        + ", maxYields="
        + maxYields
        + ", minParkPeriod="
        + minParkPeriod
        + ", maxParkPeriod="
        + maxParkPeriod
        + '}';
  }

  private record IdleStrategySupplier(IdleStrategyConfig config) implements Supplier<IdleStrategy> {
    @Override
    public IdleStrategy get() {
      return new BackoffIdleStrategy(
          config.getMaxSpins(),
          config.getMaxYields(),
          config.getMinParkPeriod().toNanos(),
          config.getMaxParkPeriod().toNanos());
    }
  }
}
