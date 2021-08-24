/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;

public final class ProcessMessageSubscriptionCorrelatedApplier
    implements TypedEventApplier<
        ProcessMessageSubscriptionIntent, ProcessMessageSubscriptionRecord> {

  private final MutableProcessMessageSubscriptionState subscriptionState;
  private final MutableVariableState variableState;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public ProcessMessageSubscriptionCorrelatedApplier(
      final MutableProcessMessageSubscriptionState subscriptionState,
      final MutableVariableState variableState,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState) {
    this.subscriptionState = subscriptionState;
    this.variableState = variableState;
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void applyState(final long key, final ProcessMessageSubscriptionRecord value) {
    final var eventScopeKey = value.getElementInstanceKey();

    if (value.isInterrupting()) {
      subscriptionState.remove(eventScopeKey, value.getMessageNameBuffer());
    } else {
      // if the message subscription is created and a matching message is buffered then it writes a
      // process message subscription CORRELATE instead of a CREATE command
      subscriptionState.updateToOpenedState(value);
    }

    if (shouldCreateTemporaryVariables(value)) {
      variableState.setTemporaryVariables(eventScopeKey, value.getVariablesBuffer());
    }
  }

  // temporary variables are being replaced with local variables and variables on the event trigger
  // try to reduce the number of cases this method returns true
  private boolean shouldCreateTemporaryVariables(final ProcessMessageSubscriptionRecord value) {
    if (value.getVariablesBuffer().capacity() <= 0) {
      return false;
    }

    final var eventScopeKey = value.getElementInstanceKey();
    final var eventScopeInstance = elementInstanceState.getInstance(eventScopeKey);
    if (eventScopeInstance == null) {
      // unexpected...
      return false;
    }

    // the event element is the specific element that the message was correlated to
    // i.e. the boundary event, the intermediary event, the start event, etc
    final ExecutableFlowElement eventElement =
        processState.getFlowElement(
            eventScopeInstance.getValue().getProcessDefinitionKey(),
            value.getElementIdBuffer(),
            ExecutableFlowElement.class);
    if (eventElement == null) {
      // unexpected...
      return false;
    }

    switch (eventElement.getElementType()) {
      case INTERMEDIATE_CATCH_EVENT:
      case RECEIVE_TASK:
      case START_EVENT:
        return true;
      default:
        return false;
    }
  }
}
