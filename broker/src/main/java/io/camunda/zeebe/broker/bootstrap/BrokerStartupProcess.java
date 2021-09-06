/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.startup.StartupProcess;
import org.slf4j.Logger;

public final class BrokerStartupProcess {

  public static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final ConcurrencyControl concurrencyControl;

  private final StartupProcess<BrokerStartupContext> startupProcess =
      new StartupProcess<>(LOG, emptyList());

  public BrokerStartupProcess(final ConcurrencyControl concurrencyControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
  }

  public ActorFuture<BrokerContext> start() {
    final ActorFuture<BrokerContext> result = concurrencyControl.createFuture();

    final var startupContext = new BrokerStartupContextImpl();

    final var startupFuture = startupProcess.startup(concurrencyControl, startupContext);

    concurrencyControl.runOnCompletion(
        startupFuture,
        (bsc, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(createBrokerContext(bsc));
          }
        });
    return result;
  }

  public ActorFuture<Void> stop() {
    final ActorFuture<Void> result = concurrencyControl.createFuture();

    final var shutdownContext = new BrokerStartupContextImpl();

    final var startupFuture = startupProcess.shutdown(concurrencyControl, shutdownContext);

    concurrencyControl.runOnCompletion(
        startupFuture,
        (bsc, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(null);
          }
        });
    return result;
  }

  private BrokerContext createBrokerContext(final BrokerStartupContext bsc) {
    return new BrokerContextImpl();
  }
}
