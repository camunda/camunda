/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backpressure;

import com.netflix.concurrency.limits.Limit;
import io.camunda.zeebe.broker.system.configuration.backpressure.LimitCfg;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** A request limiter that manages the limits for each partition independently. */
public final class PartitionAwareRequestLimiter {

  private final Map<Integer, RequestLimiter<Intent>> partitionLimiters = new ConcurrentHashMap<>();

  private final Function<Integer, RequestLimiter<Intent>> limiterSupplier;

  private PartitionAwareRequestLimiter(final Limit limit) {
    if (limit == null) {
      limiterSupplier = i -> new NoopRequestLimiter<>();
    } else {
      limiterSupplier = i -> CommandRateLimiter.builder().limit(limit).build(i);
    }
  }

  public static PartitionAwareRequestLimiter newLimiter(final LimitCfg limitCfg) {
    return new PartitionAwareRequestLimiter(limitCfg.buildLimit());
  }

  public boolean tryAcquire(
      final int partitionId, final int streamId, final long requestId, final Intent context) {
    final RequestLimiter<Intent> limiter = getLimiter(partitionId);
    return limiter.tryAcquire(streamId, requestId, context);
  }

  public void onResponse(final int partitionId, final int streamId, final long requestId) {
    final RequestLimiter<Intent> limiter = partitionLimiters.get(partitionId);
    if (limiter != null) {
      limiter.onResponse(streamId, requestId);
    }
  }

  public void addPartition(final int partitionId) {
    removePartition(partitionId);
    getOrCreateLimiter(partitionId);
  }

  public void removePartition(final int partitionId) {
    partitionLimiters.remove(partitionId);
  }

  public RequestLimiter<Intent> getLimiter(final int partitionId) {
    return getOrCreateLimiter(partitionId);
  }

  private RequestLimiter<Intent> getOrCreateLimiter(final int partitionId) {
    return partitionLimiters.computeIfAbsent(partitionId, limiterSupplier);
  }
}
