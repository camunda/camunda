/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static io.camunda.zeebe.util.sched.future.CompletableActorFuture.completedExceptionally;

import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.startup.StartupStep;
import java.util.function.BiConsumer;

abstract class AbstractBrokerStartupStep implements StartupStep<BrokerStartupContext> {

  @Override
  public final ActorFuture<BrokerStartupContext> startup(
      final BrokerStartupContext brokerStartupContext) {
    return createFutureAndRun(
        brokerStartupContext,
        (concurrencyControl, future) ->
            startupInternal(brokerStartupContext, concurrencyControl, future));
  }

  @Override
  public final ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerShutdownContext) {
    return createFutureAndRun(
        brokerShutdownContext,
        (concurrencyControl, future) ->
            shutdownInternal(brokerShutdownContext, concurrencyControl, future));
  }

  abstract void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture);

  abstract void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture);

  /**
   * Helper function that tries to create a future and call a runnable. If an exception is thrown
   * while creating the future, then this exception is forwarded to a dummy future
   */
  final ActorFuture<BrokerStartupContext> createFutureAndRun(
      final BrokerStartupContext brokerStartupContext,
      final BiConsumer<ConcurrencyControl, ActorFuture<BrokerStartupContext>> runnable) {
    try {
      final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
      final ActorFuture<BrokerStartupContext> future = concurrencyControl.createFuture();

      forwardExceptions(() -> runnable.accept(concurrencyControl, future), future);
      return future;
    } catch (final Exception e) {
      return completedExceptionally(e);
    }
  }
  /**
   * helper function that forwards exceptions thrown by a synchronous block of code to a future
   * object
   */
  final <V> void forwardExceptions(final Runnable r, final ActorFuture<V> future) {
    try {
      r.run();
    } catch (final Exception e) {
      future.completeExceptionally(e);
    }
  }
}
