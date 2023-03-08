/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers.v1;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessEventRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;

public final class ProcessEventTriggeredApplier
    implements TypedEventApplier<ProcessEventIntent, ProcessEventRecord> {
  private final MutableEventScopeInstanceState eventScopeState;

  public ProcessEventTriggeredApplier(final MutableEventScopeInstanceState eventScopeState) {
    this.eventScopeState = eventScopeState;
  }

  @Override
  public void applyState(final long key, final ProcessEventRecord value) {
    final var scopeKey = value.getScopeKey();
    eventScopeState.deleteTrigger(scopeKey, key);
  }
}
