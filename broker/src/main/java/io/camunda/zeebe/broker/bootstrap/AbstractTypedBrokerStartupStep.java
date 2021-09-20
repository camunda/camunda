/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

abstract class AbstractTypedBrokerStartupStep<INPUT, OUTPUT> extends AbstractBrokerStartupStep {

  private final Function<BrokerStartupContext, INPUT> inputConverter;
  private final BiFunction<BrokerStartupContext, OUTPUT, BrokerStartupContext> outputConverter;

  public AbstractTypedBrokerStartupStep(
      final Function<BrokerStartupContext, INPUT> inputConverter,
      final BiFunction<BrokerStartupContext, OUTPUT, BrokerStartupContext> outputConverter) {
    this.inputConverter = Objects.requireNonNull(inputConverter);
    this.outputConverter = Objects.requireNonNull(outputConverter);
  }

  @Override
  final void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var input = inputConverter.apply(brokerStartupContext);
    final ActorFuture<OUTPUT> outputFuture = concurrencyControl.createFuture();

    startupTyped(input, concurrencyControl, outputFuture);

    concurrencyControl.runOnCompletion(
        outputFuture,
        (output, error) -> {
          if (error != null) {
            startupFuture.completeExceptionally(error);
            return;
          }

          startupFuture.complete(outputConverter.apply(brokerStartupContext, output));
        });
  }

  @Override
  final void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var input = inputConverter.apply(brokerShutdownContext);
    final ActorFuture<OUTPUT> outputFuture = concurrencyControl.createFuture();

    shutdownTyped(input, concurrencyControl, outputFuture);

    concurrencyControl.runOnCompletion(
        outputFuture,
        (output, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
            return;
          }

          shutdownFuture.complete(outputConverter.apply(brokerShutdownContext, output));
        });
  }

  abstract void startupTyped(
      INPUT input, ConcurrencyControl concurrencyControl, ActorFuture<OUTPUT> startupFuture);

  abstract void shutdownTyped(
      INPUT input, ConcurrencyControl concurrencyControl, ActorFuture<OUTPUT> shutdownFuture);
}
