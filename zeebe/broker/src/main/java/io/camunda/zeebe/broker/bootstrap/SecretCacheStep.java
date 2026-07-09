/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.engine.processing.secret.InMemorySecretCache;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

/**
 * Allocates the broker-wide secret cache once at startup and stores it on the {@link
 * BrokerStartupContext}, so every partition's engine shares the same instance.
 */
final class SecretCacheStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Secret Cache";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(
        () -> {
          brokerStartupContext.setSecretCache(new InMemorySecretCache());
          startupFuture.complete(brokerStartupContext);
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    concurrencyControl.run(
        () -> {
          brokerShutdownContext.setSecretCache(null);
          shutdownFuture.complete(brokerShutdownContext);
        });
  }
}
