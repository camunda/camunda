/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Gives a physical tenant its own actor scheduler when the physical tenant actor pool is enabled,
 * so its partition actors cannot take the actor threads of other tenants. Must run before the
 * tenant's {@link PartitionManagerStep}, so that it is stopped after it on shutdown.
 */
final class PhysicalTenantActorSchedulerStep extends AbstractBrokerStartupStep {

  private final String physicalTenantId;
  private MeterRegistry meterRegistry;

  PhysicalTenantActorSchedulerStep(final String physicalTenantId) {
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public String getName() {
    return "Physical Tenant Actor Scheduler [" + physicalTenantId + "]";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(
        () -> {
          try {
            final var threads =
                brokerStartupContext
                    .getPhysicalTenantContext(physicalTenantId)
                    .config()
                    .getThreads();
            if (threads.isPhysicalTenantActorPoolEnabled()) {
              final var builder = brokerStartupContext.newActorSchedulerBuilder();
              // tags every actor metric of the pool; null when actor metrics are disabled
              if (builder.getMeterRegistry() != null) {
                meterRegistry =
                    MicrometerUtil.wrap(
                        builder.getMeterRegistry(),
                        Tags.of(PartitionKeyNames.PHYSICAL_TENANT.asString(), physicalTenantId));
                builder.setMeterRegistry(meterRegistry);
              }
              final var scheduler =
                  builder
                      .setSchedulerName(builder.getSchedulerName() + "-" + physicalTenantId)
                      .setThreadNamePrefix(physicalTenantId + "-")
                      .setCpuBoundActorThreadCount(threads.getPhysicalTenantCpuThreadCount())
                      .setIoBoundActorThreadCount(threads.getPhysicalTenantIoThreadCount())
                      .build();
              scheduler.start();
              brokerStartupContext.addPhysicalTenantActorScheduler(physicalTenantId, scheduler);
            }
            startupFuture.complete(brokerStartupContext);
          } catch (final Exception e) {
            startupFuture.completeExceptionally(e);
          }
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    // the scheduler is only used by this tenant's partitions, which are already stopped
    final var scheduler =
        brokerShutdownContext.removePhysicalTenantActorScheduler(physicalTenantId);
    if (scheduler == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    // stopping waits for the scheduler threads, which must not block the broker actor
    CompletableFuture.runAsync(
            () -> {
              try {
                scheduler.close();
              } catch (final Exception e) {
                throw new CompletionException(e);
              }
            })
        .whenComplete(
            (ok, error) ->
                concurrencyControl.run(
                    () -> {
                      MicrometerUtil.close(meterRegistry);
                      if (error != null) {
                        shutdownFuture.completeExceptionally(error);
                      } else {
                        shutdownFuture.complete(brokerShutdownContext);
                      }
                    }));
  }
}
