/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.WorkflowRecord;
import io.zeebe.protocol.record.intent.WorkflowIntent;
import java.util.Collections;

public class WorkflowCreatedApplier implements TypedEventApplier<WorkflowIntent, WorkflowRecord> {

  private final MutableWorkflowState workflowState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  public WorkflowCreatedApplier(final ZeebeState state) {
    workflowState = state.getWorkflowState();
    eventScopeInstanceState = state.getEventScopeInstanceState();
  }

  @Override
  public void applyState(final long workflowKey, final WorkflowRecord value) {
    workflowState.putWorkflow(workflowKey, value);

    // timer start events
    final var hasAtLeastOneTimer =
        workflowState.getWorkflowByKey(workflowKey).getWorkflow().getStartEvents().stream()
            .anyMatch(ExecutableCatchEventElement::isTimer);

    if (hasAtLeastOneTimer) {
      eventScopeInstanceState.createIfNotExists(workflowKey, Collections.emptyList());
    }
  }
}
