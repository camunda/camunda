/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.engine.state.instance.TimerInstance.NO_ELEMENT_INSTANCE;

import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationException;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.StartEventSubscriptionManager;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.model.bpmn.util.time.Timer;
import io.camunda.zeebe.util.Either;

public final class StartEventSubscriptions {

  private final ExpressionProcessor expressionProcessor;
  private final CatchEventBehavior catchEventBehavior;
  private final StartEventSubscriptionManager startEventSubscriptionManager;

  public StartEventSubscriptions(
      final ExpressionProcessor expressionProcessor,
      final CatchEventBehavior catchEventBehavior,
      final StartEventSubscriptionManager startEventSubscriptionManager) {
    this.expressionProcessor = expressionProcessor;
    this.catchEventBehavior = catchEventBehavior;
    this.startEventSubscriptionManager = startEventSubscriptionManager;
  }

  public void resubscribeToStartEvents(final DeployedProcess deployedProcess) {
    final var process = deployedProcess.getProcess();
    if (process.hasTimerStartEvent()) {
      process.getStartEvents().stream()
          .filter(ExecutableCatchEventElement::isTimer)
          .forEach(
              timerStartEvent -> {
                final Either<Failure, Timer> failureOrTimer =
                    timerStartEvent
                        .getTimerFactory()
                        .apply(expressionProcessor, NO_ELEMENT_INSTANCE);

                if (failureOrTimer.isLeft()) {
                  throw new EvaluationException(failureOrTimer.getLeft().getMessage());
                }

                catchEventBehavior.subscribeToTimerEvent(
                    NO_ELEMENT_INSTANCE,
                    NO_ELEMENT_INSTANCE,
                    deployedProcess.getKey(),
                    timerStartEvent.getId(),
                    deployedProcess.getTenantId(),
                    failureOrTimer.get());
              });
    }

    startEventSubscriptionManager.openStartEventSubscriptions(deployedProcess);
  }
}
