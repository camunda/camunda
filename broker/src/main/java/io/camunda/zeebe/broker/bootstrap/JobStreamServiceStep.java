/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.jobstream.JobPusher;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public final class JobStreamServiceStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var scheduler = brokerStartupContext.getActorSchedulingService();
    final var pusher = new JobPusher(brokerStartupContext.getClusterServices());

    final var startup = scheduler.submitActor(pusher);
    concurrencyControl.runOnCompletion(
        startup,
        (ok, error) -> {
          if (error != null) {
            startupFuture.completeExceptionally(error);
          } else {
            brokerStartupContext.setJobPusher(pusher);
            startupFuture.complete(brokerStartupContext);
          }
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var pusher = brokerShutdownContext.getJobPusher();
    if (pusher == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    brokerShutdownContext.setJobPusher(null);

    final var shutdown = pusher.closeAsync();
    concurrencyControl.runOnCompletion(
        shutdown,
        (ok, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }

  @Override
  public String getName() {
    return "Job Stream";
  }
}
