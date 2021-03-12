/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableProcessState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.zeebe.protocol.record.intent.ProcessIntent;
import java.util.Collections;

public class ProcessCreatedApplier implements TypedEventApplier<ProcessIntent, ProcessRecord> {

  private final MutableProcessState processState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  public ProcessCreatedApplier(final MutableZeebeState state) {
    processState = state.getProcessState();
    eventScopeInstanceState = state.getEventScopeInstanceState();
  }

  @Override
  public void applyState(final long processDefinitionKey, final ProcessRecord value) {
    processState.putProcess(processDefinitionKey, value);

    // timer start events
    final var hasAtLeastOneTimer =
        processState.getProcessByKey(processDefinitionKey).getProcess().getStartEvents().stream()
            .anyMatch(ExecutableCatchEventElement::isTimer);

    if (hasAtLeastOneTimer) {
      eventScopeInstanceState.createIfNotExists(processDefinitionKey, Collections.emptyList());
    }
  }
}
