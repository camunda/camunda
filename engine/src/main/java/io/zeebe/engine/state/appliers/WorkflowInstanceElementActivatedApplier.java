/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.EventApplier;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

/**
 * This class represents an example to apply state changes for `WorkflowInstance:Element_Activated`
 */
final class WorkflowInstanceElementActivatedApplier
    implements EventApplier<WorkflowInstanceIntent, WorkflowInstanceRecord> {

  WorkflowInstanceElementActivatedApplier(final ZeebeState state) {}

  @Override
  public void applyState(final long key, final WorkflowInstanceRecord value) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
