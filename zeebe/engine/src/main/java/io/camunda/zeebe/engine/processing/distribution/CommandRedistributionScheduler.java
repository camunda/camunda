/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.distribution;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries sending {@link CommandDistributionRecord}s to other partitions, using exponential backoff
 * per distribution.
 *
 * <p>Periodic, fixed cadence: returns {@link Result.Builder#idle()} on every run; the runtime fires
 * us again at the configured fallback interval. Inter-partition sends go through the (legacy)
 * {@link io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior}, which
 * retains its own {@link io.camunda.zeebe.stream.api.InterPartitionCommandSender} reference for
 * historical reasons.
 *
 * <p>{@code retryCyclesPerDistribution} is accumulated state across runs, not a resume cursor — it
 * survives between runs as an instance field rather than via {@code TaskContext.resumeCursor()}.
 *
 * <p>Whether to skip running entirely (operator pause) is enforced at wiring time: when {@code
 * config.isCommandDistributionPaused()} is set, the runtime is simply not registered.
 */
public final class CommandRedistributionScheduler implements ScheduledTask<Void> {

  private static final Logger LOG = LoggerFactory.getLogger(CommandRedistributionScheduler.class);

  private final CommandDistributionBehavior distributionBehavior;
  private final RoutingInfo routingInfo;
  private final long maxRetryCycles;
  private final Map<RetriableDistribution, Long> retryCyclesPerDistribution = new HashMap<>();

  public CommandRedistributionScheduler(
      final CommandDistributionBehavior distributionBehavior,
      final RoutingInfo routingInfo,
      final EngineConfiguration config) {
    this.distributionBehavior = distributionBehavior;
    this.routingInfo = routingInfo;
    final Duration interval = config.getCommandRedistributionInterval();
    maxRetryCycles = config.getCommandRedistributionMaxBackoff().dividedBy(interval);
  }

  @Override
  public String name() {
    return "command-redistribution";
  }

  @Override
  public Result run(final TaskContext<Void> ctx) {
    final Result.Builder<Void> result = ctx.result();
    final HashSet<RetriableDistribution> visited = new HashSet<>();
    distributionBehavior.foreachRetriableDistribution(
        (distributionKey, record) -> {
          if (routingInfo.isPartitionScaling(record.getPartitionId())) {
            LOG.debug(
                "Excluding distribution {} for partition {} as it is currently scaling up.",
                distributionKey,
                record.getPartitionId());
            return true;
          }

          final RetriableDistribution retriable =
              RetriableDistribution.from(distributionKey, record);
          final long retryCycle = updateRetryCycle(retriable);

          if (retriable.shouldRetryNow(retryCycle, maxRetryCycles)) {
            LOG.info(
                "Retrying to distribute retriable command {} ({}.{}) to partition {} (Cycle: #{})",
                distributionKey,
                record.getValueType(),
                record.getIntent(),
                record.getPartitionId(),
                retryCycle);
            distributionBehavior.onScheduledRetry(distributionKey, record);
          }

          visited.add(retriable);
          return true;
        });

    // Drop tracking for distributions that have been completed since the last run.
    retryCyclesPerDistribution.keySet().removeIf(Predicate.not(visited::contains));

    return result.idle();
  }

  private long updateRetryCycle(final RetriableDistribution retriable) {
    return retryCyclesPerDistribution.compute(
        retriable, (k, retryCycles) -> retryCycles != null ? retryCycles + 1 : 0L);
  }

  private record RetriableDistribution(long distributionKey, int partitionId) {
    boolean shouldRetryNow(final long retryCycle, final long maxRetryCycles) {
      if (retryCycle >= maxRetryCycles) {
        return retryCycle % maxRetryCycles == 0;
      }
      return Long.bitCount(retryCycle) == 1;
    }

    static RetriableDistribution from(
        final long distributionKey, final CommandDistributionRecord record) {
      return new RetriableDistribution(distributionKey, record.getPartitionId());
    }
  }
}
