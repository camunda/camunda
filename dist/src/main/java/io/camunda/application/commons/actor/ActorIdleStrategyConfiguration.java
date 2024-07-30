/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.actor;

import io.camunda.application.commons.actor.ActorIdleStrategyConfiguration.IdleStrategyProperties;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
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
public final class ActorIdleStrategyConfiguration {
  private final IdleStrategyProperties properties;

  @Autowired
  public ActorIdleStrategyConfiguration(final IdleStrategyProperties properties) {
    this.properties = properties;
  }

  public ActorIdleStrategyConfiguration() {
    this(new IdleStrategyProperties(null, null, null, null));
  }

  @Bean
  public IdleStrategySupplier toSupplier() {
    return new IdleStrategySupplier(
        properties.maxSpins(),
        properties.maxYields(),
        properties.minParkPeriodNs(),
        properties.maxParkPeriodNs());
  }

  @ConfigurationProperties(prefix = "zeebe.actor.idle")
  public record IdleStrategyProperties(
      @Nullable Long maxSpins,
      @Nullable Long maxYields,
      @Nullable Duration minParkPeriod,
      @Nullable Duration maxParkPeriod) {
    @Override
    public Long maxSpins() {
      return maxSpins == null ? ActorSchedulerBuilder.DEFAULT_MAX_SPINS : maxSpins;
    }

    @Override
    public Long maxYields() {
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

  public record IdleStrategySupplier(
      long maxSpins, long maxYields, long minParkPeriodNs, long maxParkPeriodNs)
      implements Supplier<IdleStrategy> {

    @Override
    public IdleStrategy get() {
      return new BackoffIdleStrategy(maxSpins, maxYields, minParkPeriodNs, maxParkPeriodNs);
    }

    public static IdleStrategySupplier ofDefault() {
      return new IdleStrategySupplier(
          ActorSchedulerBuilder.DEFAULT_MAX_SPINS,
          ActorSchedulerBuilder.DEFAULT_MAX_YIELDS,
          ActorSchedulerBuilder.DEFAULT_MIN_PARK_PERIOD_NS,
          ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS);
    }
  }
}
