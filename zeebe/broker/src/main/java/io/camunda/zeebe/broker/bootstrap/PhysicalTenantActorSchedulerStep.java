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
import java.util.concurrent.CompletableFuture;

/**
 * Gives a physical tenant its own actor scheduler, so its partition actors cannot take the actor
 * threads of other tenants. Must run before the tenant's {@link PartitionManagerStep}, so that it
 * is stopped after it on shutdown.
 */
final class PhysicalTenantActorSchedulerStep extends AbstractBrokerStartupStep {

  private final String physicalTenantId;

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
            final var pool =
                brokerStartupContext
                    .getPhysicalTenantContext(physicalTenantId)
                    .config()
                    .getThreads()
                    .getPhysicalTenantActorPool();
            brokerStartupContext.addPhysicalTenantActorScheduler(
                physicalTenantId,
                PhysicalTenantActorScheduler.start(
                    brokerStartupContext.newActorSchedulerBuilder(), physicalTenantId, pool));
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

    // closing blocks until the scheduler threads stop, which must not block the broker actor
    CompletableFuture.runAsync(scheduler::close)
        .whenComplete(
            (ok, error) ->
                concurrencyControl.run(
                    () -> {
                      if (error != null) {
                        shutdownFuture.completeExceptionally(error);
                      } else {
                        shutdownFuture.complete(brokerShutdownContext);
                      }
                    }));
  }
}
