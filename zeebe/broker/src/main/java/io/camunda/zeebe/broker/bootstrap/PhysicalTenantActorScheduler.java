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
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;

/**
 * The actor scheduler dedicated to a physical tenant, together with the registry that tags its
 * actor metrics with the tenant. Closing it stops the scheduler and discards the registry.
 *
 * @param meterRegistry the tagging registry, or {@code null} if actor metrics are disabled
 */
record PhysicalTenantActorScheduler(ActorScheduler scheduler, @Nullable MeterRegistry meterRegistry)
    implements AutoCloseable {

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
    final var scheduler =
        builder
            .setMeterRegistry(meterRegistry)
            .setThreadNamePrefix(physicalTenantId + "-")
            .setCpuBoundActorThreadCount(pool.getCpuThreadCount())
            .setIoBoundActorThreadCount(pool.getIoThreadCount())
            .build();
    scheduler.start();
    return new PhysicalTenantActorScheduler(scheduler, meterRegistry);
  }

  /** Blocks until the scheduler threads have stopped. */
  @Override
  public void close() {
    try {
      scheduler.close();
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to stop physical tenant actor scheduler", e);
    } finally {
      MicrometerUtil.close(meterRegistry);
    }
  }
}
