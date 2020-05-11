/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.backpressure;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Limiter.Listener;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import java.util.Optional;
import java.util.function.Supplier;
import org.agrona.collections.Int2ObjectHashMap;

public final class PartitionAwareRequestLimiterImpl implements PartitionAwareRequestLimiter {
  private final Int2ObjectHashMap<Limiter<BrokerRequest<?>>> partitionedLimiters;
  private final Supplier<Limit> limitSupplier;

  public PartitionAwareRequestLimiterImpl(final Supplier<Limit> limitSupplier) {
    this.limitSupplier = limitSupplier;
    this.partitionedLimiters = new Int2ObjectHashMap<>();
  }

  @Override
  public Optional<Listener> acquire(final int partitionId, final BrokerRequest<?> request) {
    final Limiter<BrokerRequest<?>> limiter =
        partitionedLimiters.computeIfAbsent(partitionId, this::createLimiter);
    return limiter.acquire(request);
  }

  private Limiter<BrokerRequest<?>> createLimiter(final int partitionId) {
    return BrokerRequestLimiter.newBuilder()
        .withPartitionId(partitionId)
        .limit(limitSupplier.get())
        .build();
  }
}
