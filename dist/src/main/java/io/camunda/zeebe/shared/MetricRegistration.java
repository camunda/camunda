/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.netty4.NettyAllocatorMetrics;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class MetricRegistration implements MeterBinder {

  @SuppressWarnings("NullableProblems")
  @Override
  public void bindTo(final MeterRegistry registry) {
    new NettyAllocatorMetrics(PooledByteBufAllocator.DEFAULT).bindTo(registry);
    new NettyAllocatorMetrics(UnpooledByteBufAllocator.DEFAULT).bindTo(registry);
  }
}
