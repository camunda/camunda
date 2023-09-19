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
import org.springframework.context.annotation.Configuration;

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

  public Supplier<IdleStrategy> toSupplier() {
    final var maxSpins = properties.maxSpins();
    final var maxYields = properties.maxYields();
    final var minParkPeriod = properties.minParkPeriodNs();
    final var maxParkPeriod = properties.maxParkPeriodNs();

    return () -> new BackoffIdleStrategy(maxSpins, maxYields, minParkPeriod, maxParkPeriod);
  }

  @ConfigurationProperties(prefix = "zeebe.actor.idle")
  public record IdleStrategyProperties(
      Integer maxSpins, Integer maxYields, Duration minParkPeriod, Duration maxParkPeriod) {
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
}
