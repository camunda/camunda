/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;

final class TimerCancelledApplier implements TypedEventApplier<TimerIntent, TimerRecord> {

  private final MutableTimerInstanceState timerInstanceState;
  private final MutableProcessState processState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  TimerCancelledApplier(
      final MutableTimerInstanceState timerInstanceState,
      final MutableProcessState processState,
      final MutableEventScopeInstanceState eventScopeInstanceState) {
    this.timerInstanceState = timerInstanceState;
    this.processState = processState;
    this.eventScopeInstanceState = eventScopeInstanceState;
  }

  @Override
  public void applyState(final long key, final TimerRecord value) {
    final TimerInstance timerInstance = timerInstanceState.get(value.getElementInstanceKey(), key);
    timerInstanceState.remove(timerInstance);

    final var process = processState.getProcessByKey(value.getProcessDefinitionKey());
    process.getProcess().getStartEvents().stream()
        .filter(ExecutableCatchEventElement::isTimer)
        .filter(
            executableStartEvent ->
                executableStartEvent.getId().equals(value.getTargetElementIdBuffer()))
        .findAny()
        .ifPresent(
            executableStartEvent -> eventScopeInstanceState.deleteInstance(process.getKey()));
  }
}
