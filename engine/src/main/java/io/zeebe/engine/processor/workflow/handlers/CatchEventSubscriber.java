/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.ExpressionProcessor.EvaluationException;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventSupplier;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyException;
import io.zeebe.protocol.record.value.ErrorType;

public final class CatchEventSubscriber {
  private final CatchEventBehavior catchEventBehavior;

  public CatchEventSubscriber(final CatchEventBehavior catchEventBehavior) {
    this.catchEventBehavior = catchEventBehavior;
  }

  public <T extends ExecutableCatchEventSupplier> boolean subscribeToEvents(
      final BpmnStepContext<T> context) {
    try {
      catchEventBehavior.subscribeToEvents(context, context.getElement());
      return true;
    } catch (final MessageCorrelationKeyException e) {
      context.raiseIncident(
          ErrorType.EXTRACT_VALUE_ERROR, e.getContext().getVariablesScopeKey(), e.getMessage());
    } catch (final EvaluationException e) {
      context.raiseIncident(ErrorType.EXTRACT_VALUE_ERROR, e.getMessage());
    }

    return false;
  }

  public <T extends ExecutableCatchEventSupplier> void unsubscribeFromEvents(
      final BpmnStepContext<T> context) {
    catchEventBehavior.unsubscribeFromEvents(context.getKey(), context);
  }
}
