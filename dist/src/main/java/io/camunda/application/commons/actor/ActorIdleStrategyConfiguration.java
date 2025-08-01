/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.actor;

import io.camunda.configuration.beans.IdleStrategyBasedProperties;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import java.util.function.Supplier;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({IdleStrategyBasedProperties.class})
public final class ActorIdleStrategyConfiguration {
  private final IdleStrategyBasedProperties properties;

  @Autowired
  public ActorIdleStrategyConfiguration(final IdleStrategyBasedProperties properties) {
    this.properties = properties;
  }

  public ActorIdleStrategyConfiguration() {
    this(new IdleStrategyBasedProperties(null, null, null, null));
  }

  @Bean
  public IdleStrategySupplier toSupplier() {
    return new IdleStrategySupplier(
        properties.maxSpins(),
        properties.maxYields(),
        properties.minParkPeriodNs(),
        properties.maxParkPeriodNs());
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
