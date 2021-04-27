/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableProcessState;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.zeebe.protocol.record.intent.ProcessEventIntent;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

final class ProcessEventTriggeringApplier
    implements TypedEventApplier<ProcessEventIntent, ProcessEventRecord> {
  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();
  private final MutableEventScopeInstanceState eventScopeState;
  private final EventSubProcessInterruptionMarker eventSubProcessInterruptionMarker;

  public ProcessEventTriggeringApplier(
      final MutableEventScopeInstanceState eventScopeState,
      final MutableElementInstanceState elementInstanceState,
      final MutableProcessState processState) {
    this.eventScopeState = eventScopeState;
    eventSubProcessInterruptionMarker =
        new EventSubProcessInterruptionMarker(processState, elementInstanceState);
  }

  @Override
  public void applyState(final long key, final ProcessEventRecord value) {
    var variables = value.getVariablesBuffer();
    if (variables.equals(DocumentValue.EMPTY_DOCUMENT)) {
      // avoid storing an empty document
      variables = NO_VARIABLES;
    }

    final var targetElementIdBuffer = value.getTargetElementIdBuffer();
    final var scopeKey = value.getScopeKey();

    eventScopeState.triggerEvent(scopeKey, key, targetElementIdBuffer, variables);
    eventSubProcessInterruptionMarker.markInstanceIfInterrupted(
        scopeKey, value.getProcessDefinitionKey(), targetElementIdBuffer);
  }
}
