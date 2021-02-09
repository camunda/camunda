/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.mutable.MutableWorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.WorkflowRecord;
import io.zeebe.protocol.record.intent.WorkflowIntent;

public class WorkflowCreatedApplier implements TypedEventApplier<WorkflowIntent, WorkflowRecord> {

  private final MutableWorkflowState workflowState;

  public WorkflowCreatedApplier(final ZeebeState state) {
    workflowState = state.getWorkflowState();
  }

  @Override
  public void applyState(final long workflowKey, final WorkflowRecord value) {
    workflowState.putWorkflow(workflowKey, value);
  }
}
