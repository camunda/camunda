/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.system.monitoring.BrokerStepMetrics;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import io.camunda.zeebe.util.startup.StartupStep;
import java.util.function.BiConsumer;
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
    return callDelegateAndUpdateMetrics(
        brokerStartupContext, delegate::startup, brokerStepMetrics::observeDurationForStarStep);
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerStartupContext) {
    return callDelegateAndUpdateMetrics(
        brokerStartupContext, delegate::shutdown, brokerStepMetrics::observeDurationForCloseStep);
  }

  private ActorFuture<BrokerStartupContext> callDelegateAndUpdateMetrics(
      final BrokerStartupContext brokerStartupContext,
      final Function<BrokerStartupContext, ActorFuture<BrokerStartupContext>> functionToCall,
      final BiConsumer<String, Long> metricUpdater) {
    try {
      final long startTime = System.currentTimeMillis();

      final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
      final var future = functionToCall.apply(brokerStartupContext);
      concurrencyControl.runOnCompletion(
          future,
          (nil, error) -> {
            final long durationStepStarting = System.currentTimeMillis() - startTime;
            metricUpdater.accept(delegate.getName(), durationStepStarting);
          });

      return future;
    } catch (final Throwable t) {
      return CompletableActorFuture.completedExceptionally(t);
    }
  }
}
