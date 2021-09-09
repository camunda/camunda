/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static io.camunda.zeebe.util.sched.future.CompletableActorFuture.completedExceptionally;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.system.monitoring.BrokerStepMetrics;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.startup.StartupStep;
import io.prometheus.client.Gauge.Timer;
import java.util.function.Function;

/**
 * Decorator that measures the execution time of a step and updated metrics accordingly. Note that
 * this decorator cannot measure directly the time between the start and the end of the step.
 * Instead, it measures the time between the start of the step and whenever the followup task to
 * update the metrics gets executed.
 */
final class BrokerStepMetricDecorator implements StartupStep<BrokerStartupContext> {

  private final BrokerStepMetrics brokerStepMetrics;
  private final StartupStep<BrokerStartupContext> delegate;

  BrokerStepMetricDecorator(
      final BrokerStepMetrics brokerStepMetrics, final StartupStep<BrokerStartupContext> delegate) {
    this.brokerStepMetrics = requireNonNull(brokerStepMetrics);
    this.delegate = requireNonNull(delegate);
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(
      final BrokerStartupContext brokerStartupContext) {
    return callDelegateAndUpdateTimer(
        brokerStartupContext, delegate::startup, brokerStepMetrics.createStartupTimer(getName()));
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerStartupContext) {
    return callDelegateAndUpdateTimer(
        brokerStartupContext, delegate::shutdown, brokerStepMetrics.createCloseTimer(getName()));
  }

  private ActorFuture<BrokerStartupContext> callDelegateAndUpdateTimer(
      final BrokerStartupContext brokerStartupContext,
      final Function<BrokerStartupContext, ActorFuture<BrokerStartupContext>> functionToCall,
      final Timer timer) {

    try {
      final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
      final var future = functionToCall.apply(brokerStartupContext);
      concurrencyControl.runOnCompletion(future, (ok, error) -> timer.close());

      return future;
    } catch (final Exception e) {
      return completedExceptionally(e);
    }
  }
}
