/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

/** Applies state changes for `WorkflowInstance:Element_Activated` */
final class WorkflowInstanceElementActivatedApplier
    implements TypedEventApplier<WorkflowInstanceIntent, WorkflowInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;

  WorkflowInstanceElementActivatedApplier(final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long key, final WorkflowInstanceRecord value) {
    elementInstanceState.updateInstance(
        key, instance -> instance.setState(WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    // We store the record to use it on resolving the incident, which is no longer used after
    // migrating the incident processor.
    // In order to migrate the other processors we need to write (and here remove) the record in an
    // event applier.
    // todo: we need to remove it later
    elementInstanceState.removeStoredRecord(value.getFlowScopeKey(), key, Purpose.FAILED);
  }
}
