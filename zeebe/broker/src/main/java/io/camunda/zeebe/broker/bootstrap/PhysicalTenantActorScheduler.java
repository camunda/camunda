/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.configuration.PhysicalTenantActorPoolCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.scheduler.CpuBudget;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * The actor scheduler dedicated to a physical tenant, together with the registry that tags its
 * actor metrics with the tenant and the executor that runs its expression evaluations. Closing it
 * stops the executor and the scheduler and discards the registry.
 *
 * @param meterRegistry the tagging registry, or {@code null} if actor metrics are disabled
 * @param expressionEvaluationExecutor runs the tenant's FEEL evaluations on as many threads as the
 *     tenant has CPU-bound actor threads, so evaluations cannot take CPU from other tenants
 */
record PhysicalTenantActorScheduler(
    ActorScheduler scheduler,
    @Nullable MeterRegistry meterRegistry,
    ExecutorService expressionEvaluationExecutor,
    @Nullable CpuBudget cpuBudget)
    implements AutoCloseable {

  private static final Duration EXPRESSION_EVALUATION_THREAD_KEEP_ALIVE = Duration.ofSeconds(60);

  /**
   * Starts a scheduler for the tenant from a builder that already carries the settings shared with
   * the broker-wide scheduler.
   */
  static PhysicalTenantActorScheduler start(
      final ActorSchedulerBuilder builder,
      final String physicalTenantId,
      final PhysicalTenantActorPoolCfg pool) {
    final var meterRegistry =
        builder.getMeterRegistry() == null
            ? null
            : MicrometerUtil.wrap(
                builder.getMeterRegistry(),
                Tags.of(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId));
    final var cpuBudget =
        pool.getCpuBudgetCores() > 0
            ? new CpuBudget(pool.getCpuBudgetCores(), pool.getCpuBudgetPeriod())
            : null;
    final var scheduler =
        builder
            .setCpuBudget(cpuBudget)
            .setMeterRegistry(meterRegistry)
            .setThreadNamePrefix(physicalTenantId + "-")
            .setCpuBoundActorThreadCount(pool.getCpuThreadCount())
            .setIoBoundActorThreadCount(pool.getIoThreadCount())
            .build();
    scheduler.start();
    if (cpuBudget != null) {
      cpuBudget.start(scheduler.getThreads());
    }
    final var expressionEvaluationExecutor =
        new ThreadPoolExecutor(
            pool.getCpuThreadCount(),
            pool.getCpuThreadCount(),
            EXPRESSION_EVALUATION_THREAD_KEEP_ALIVE.toSeconds(),
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            expressionEvaluationThreadFactory(physicalTenantId));
    // threads are started on demand and exit when idle
    expressionEvaluationExecutor.allowCoreThreadTimeOut(true);
    return new PhysicalTenantActorScheduler(
        scheduler, meterRegistry, expressionEvaluationExecutor, cpuBudget);
  }

  private static ThreadFactory expressionEvaluationThreadFactory(final String physicalTenantId) {
    final var threadNumber = new AtomicInteger();
    return runnable -> {
      final var thread =
          new Thread(runnable, physicalTenantId + "-feel-" + threadNumber.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
  }

  /** Blocks until the scheduler threads have stopped. */
  @Override
  public void close() {
    try {
      if (cpuBudget != null) {
        cpuBudget.close();
      }
      expressionEvaluationExecutor.shutdownNow();
      scheduler.close();
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to stop physical tenant actor scheduler", e);
    } finally {
      MicrometerUtil.close(meterRegistry);
    }
  }
}
