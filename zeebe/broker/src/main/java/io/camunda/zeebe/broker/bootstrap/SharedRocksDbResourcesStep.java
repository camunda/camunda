/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.partitioning.RocksDbSharedCache;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;

/**
 * Allocates the broker-wide RocksDB shared cache and write buffer manager once at startup, stores
 * it on the {@link BrokerStartupContext}, and closes it on shutdown. Every {@link
 * PartitionManagerStep} consumes the same shared resources, so adding physical tenants does not
 * multiply RocksDB memory usage.
 */
final class SharedRocksDbResourcesStep extends AbstractBrokerStartupStep {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  public String getName() {
    return "Shared RocksDB Resources";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(
        () -> {
          try {
            final var resources =
                RocksDbSharedCache.allocateSharedCache(
                    brokerStartupContext.getBrokerConfiguration(),
                    brokerStartupContext.getMeterRegistry());
            brokerStartupContext.setSharedRocksDbResources(resources);
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
    concurrencyControl.run(
        () -> {
          final var resources = brokerShutdownContext.getSharedRocksDbResources();
          if (resources != null) {
            try {
              resources.close();
            } catch (final Exception e) {
              LOG.warn("Failed to close shared RocksDB resources", e);
            }
            brokerShutdownContext.setSharedRocksDbResources(null);
          }
          shutdownFuture.complete(brokerShutdownContext);
        });
  }
}
