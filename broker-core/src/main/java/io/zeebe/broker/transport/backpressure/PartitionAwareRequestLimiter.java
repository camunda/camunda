/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.backpressure;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limit.FixedLimit;
import com.netflix.concurrency.limits.limit.Gradient2Limit;
import com.netflix.concurrency.limits.limit.GradientLimit;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import io.zeebe.broker.system.configuration.AIMDCfg;
import io.zeebe.broker.system.configuration.BackpressureCfg;
import io.zeebe.broker.system.configuration.BackpressureCfg.LimitAlgorithm;
import io.zeebe.broker.system.configuration.FixedLimitCfg;
import io.zeebe.broker.system.configuration.Gradient2Cfg;
import io.zeebe.broker.system.configuration.GradientCfg;
import io.zeebe.broker.system.configuration.VegasCfg;
import io.zeebe.protocol.record.intent.Intent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/** A request limiter that manages the limits for each partition independently. */
public class PartitionAwareRequestLimiter {

  private final Map<Integer, RequestLimiter<Intent>> partitionLimiters = new ConcurrentHashMap<>();

  private final Function<Integer, RequestLimiter<Intent>> limiterSupplier;

  private PartitionAwareRequestLimiter() {
    this.limiterSupplier = i -> new NoopRequestLimiter<>();
  }

  public PartitionAwareRequestLimiter(final Supplier<Limit> limitSupplier) {
    this.limiterSupplier = i -> CommandRateLimiter.builder().limit(limitSupplier.get()).build(i);
  }

  public static PartitionAwareRequestLimiter newNoopLimiter() {
    return new PartitionAwareRequestLimiter();
  }

  public static PartitionAwareRequestLimiter newLimiter(
      BackpressureCfg backpressureCfg, boolean useWindowed) {
    final LimitAlgorithm algorithm = backpressureCfg.getAlgorithm();
    final Supplier<Limit> limit;
    switch (algorithm) {
      case AIMD:
        final AIMDCfg aimdCfg = backpressureCfg.getAimd();
        limit =
            () ->
                AIMDLimit.newBuilder()
                    .initialLimit(aimdCfg.getInitialLimit())
                    .minLimit(aimdCfg.getMinLimit())
                    .maxLimit(aimdCfg.getMaxLimit())
                    .timeout(aimdCfg.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .backoffRatio(aimdCfg.getBackoffRatio())
                    .build();
        break;
      case FIXED:
        final FixedLimitCfg fixedLimitCfg = backpressureCfg.getFixedLimit();
        limit = () -> FixedLimit.of(fixedLimitCfg.getLimit());
        break;
      case GRADIENT:
        final GradientCfg gradientCfg = backpressureCfg.getGradient();
        limit =
            () ->
                GradientLimit.newBuilder()
                    .minLimit(gradientCfg.getMinLimit())
                    .initialLimit(gradientCfg.getInitialLimit())
                    .rttTolerance(gradientCfg.getRttTolerance())
                    .build();
        break;
      case GRADIENT2:
        final Gradient2Cfg gradient2Cfg = backpressureCfg.getGradient2();
        limit =
            () ->
                Gradient2Limit.newBuilder()
                    .rttTolerance(gradient2Cfg.getRttTolerance())
                    .initialLimit(gradient2Cfg.getInitialLimit())
                    .minLimit(gradient2Cfg.getMinLimit())
                    .longWindow(gradient2Cfg.getLongWindow())
                    .build();
        break;
      case VEGAS:
      default:
        final VegasCfg vegasCfg = backpressureCfg.getVegas();
        limit =
            () ->
                VegasLimit.newBuilder()
                    .alpha(vegasCfg.getAlpha())
                    .beta(vegasCfg.getBeta())
                    .initialLimit(vegasCfg.getInitialLimit())
                    .build();
        break;
    }

    if (useWindowed) {
      return new PartitionAwareRequestLimiter(() -> WindowedLimit.newBuilder().build(limit.get()));
    } else {
      return new PartitionAwareRequestLimiter(limit);
    }
  }

  public boolean tryAcquire(int partitionId, int streamId, long requestId, Intent context) {
    final RequestLimiter<Intent> limiter = getLimiter(partitionId);
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

  public RequestLimiter<Intent> getLimiter(int partitionId) {
    return getOrCreateLimiter(partitionId);
  }

  private RequestLimiter<Intent> getOrCreateLimiter(int partitionId) {
    return partitionLimiters.computeIfAbsent(partitionId, limiterSupplier::apply);
  }
}
