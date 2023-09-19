/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.shared.IdleStrategyConfig.IdleStrategyProperties;
import java.time.Duration;
import java.util.function.Supplier;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdleStrategyProperties.class)
public final class IdleStrategyConfig {
  private final IdleStrategyProperties properties;

  @Autowired
  public IdleStrategyConfig(final IdleStrategyProperties properties) {
    this.properties = properties;
  }

  public IdleStrategyConfig() {
    this(new IdleStrategyProperties(null, null, null, null));
  }

  @Bean
  public IdleStrategySupplier toSupplier() {
    return new IdleStrategySupplierImpl(
        properties.maxSpins(),
        properties.maxYields(),
        properties.minParkPeriodNs(),
        properties.maxParkPeriodNs());
  }

  @ConfigurationProperties(prefix = "zeebe.actor.idle")
  public record IdleStrategyProperties(
      @Nullable Integer maxSpins,
      @Nullable Integer maxYields,
      @Nullable Duration minParkPeriod,
      @Nullable Duration maxParkPeriod) {
    @Override
    public Integer maxSpins() {
      return maxSpins == null ? ActorSchedulerBuilder.DEFAULT_MAX_SPINS : maxSpins;
    }

    @Override
    public Integer maxYields() {
      return maxYields == null ? ActorSchedulerBuilder.DEFAULT_MAX_YIELDS : maxYields;
    }

    public long minParkPeriodNs() {
      return minParkPeriod == null
          ? ActorSchedulerBuilder.DEFAULT_MIN_PARK_PERIOD_NS
          : minParkPeriod.toNanos();
    }

    public long maxParkPeriodNs() {
      return maxParkPeriod == null
          ? ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS
          : maxParkPeriod.toNanos();
    }
  }

  private record IdleStrategySupplierImpl(
      int maxSpins, int maxYields, long minParkPeriodNs, long maxParkPeriodNs)
      implements IdleStrategySupplier {

    @Override
    public IdleStrategy get() {
      return new BackoffIdleStrategy(maxSpins, maxYields, minParkPeriodNs, maxParkPeriodNs);
    }
  }

  /** This interface exists mostly to allow introspecting the strategy that'll we be building up. */
  public interface IdleStrategySupplier extends Supplier<IdleStrategy> {

    int maxSpins();

    int maxYields();

    long minParkPeriodNs();

    long maxParkPeriodNs();

    static IdleStrategySupplier ofDefault() {
      return new IdleStrategySupplierImpl(
          ActorSchedulerBuilder.DEFAULT_MAX_SPINS,
          ActorSchedulerBuilder.DEFAULT_MAX_YIELDS,
          ActorSchedulerBuilder.DEFAULT_MIN_PARK_PERIOD_NS,
          ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS);
    }
  }
}
