/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.backpressure;

import com.netflix.concurrency.limits.Limit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** A request limiter that manages the limits for each partition independently. */
public class PartitionAwareRequestLimiter {

  final Map<Integer, RequestLimiter<Void>> partitionLimiters = new ConcurrentHashMap<>();

  final Supplier<Limit> limitSupplier;

  public PartitionAwareRequestLimiter(Supplier<Limit> limiterSupplier) {
    this.limitSupplier = limiterSupplier;
  }

  public boolean tryAcquire(int partitionId, int streamId, long requestId, Void context) {
    final RequestLimiter<Void> limiter = getLimiter(partitionId);
    return limiter.tryAcquire(streamId, requestId, context);
  }

  public void onResponse(int partitionId, int streamId, long requestId) {
    final RequestLimiter limiter = partitionLimiters.get(partitionId);
    if (limiter != null) {
      limiter.onResponse(streamId, requestId);
    }
  }

  public void addPartition(int partitionId) {
    getOrCreateLimiter(partitionId);
  }

  public void removePartition(int partitionId) {
    partitionLimiters.remove(partitionId);
  }

  public RequestLimiter<Void> getLimiter(int partitionId) {
    return getOrCreateLimiter(partitionId);
  }

  private RequestLimiter<Void> getOrCreateLimiter(int partitionId) {
    return partitionLimiters.computeIfAbsent(
        partitionId, k -> CommandRateLimiter.builder().limit(limitSupplier.get()).build());
  }
}
