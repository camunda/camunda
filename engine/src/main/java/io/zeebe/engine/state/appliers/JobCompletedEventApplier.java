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
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableJobState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

class JobCompletedEventApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableVariableState variableState;

  JobCompletedEventApplier(final ZeebeState state) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    eventScopeInstanceState = state.getEventScopeInstanceState();
    variableState = state.getVariableState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.complete(key, value);

    final long elementInstanceKey = value.getElementInstanceKey();
    final ElementInstance elementInstance = elementInstanceState.getInstance(elementInstanceKey);

    if (elementInstance != null) {
      final long scopeKey = elementInstance.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {

        // TODO (#6172) move to somewhere else
        elementInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
        elementInstance.setJobKey(-1);
        elementInstanceState.updateInstance(elementInstance);
        // TODO move to somewhere else

        eventScopeInstanceState.shutdownInstance(elementInstanceKey);

        variableState.setTemporaryVariables(elementInstanceKey, value.getVariablesBuffer());
      }
    }
  }
}
