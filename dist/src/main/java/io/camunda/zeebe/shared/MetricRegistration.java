/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.netty4.NettyAllocatorMetrics;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.prometheus.client.CollectorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class MetricRegistration implements MeterBinder {

  @SuppressWarnings("NullableProblems")
  @Override
  public void bindTo(final MeterRegistry registry) {
    new NettyAllocatorMetrics(PooledByteBufAllocator.DEFAULT).bindTo(registry);
    new NettyAllocatorMetrics(UnpooledByteBufAllocator.DEFAULT).bindTo(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public CollectorRegistry collectorRegistry() {
    // for compatibility reasons with how Zeebe registers metrics directly to Prometheus everywhere
    // else, use the global default registry
    return CollectorRegistry.defaultRegistry;
  }
}
