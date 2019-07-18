/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.instance.WorkflowEngineState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public class EventOutput {
  private final WorkflowEngineState materializedState;
  private final KeyGenerator keyGenerator;

  private TypedStreamWriter streamWriter;

  public EventOutput(final WorkflowEngineState materializedState, final KeyGenerator keyGenerator) {
    this.materializedState = materializedState;
    this.keyGenerator = keyGenerator;
  }

  public long appendNewEvent(
      final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    return appendNewEvent(state, value, null);
  }

  public long appendNewEvent(
      final WorkflowInstanceIntent state,
      final WorkflowInstanceRecord value,
      final ExecutableFlowElement element) {

    if (element != null) {
      value.setElementId(element.getId());
      value.setBpmnElementType(element.getElementType());
    }

    final long key = keyGenerator.nextKey();
    streamWriter.appendNewEvent(key, state, value);
    materializedState.onEventProduced(key, state, value);

    return key;
  }

  public void appendFollowUpEvent(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    appendFollowUpEvent(key, state, value, null);
  }

  public void appendFollowUpEvent(
      final long key,
      final WorkflowInstanceIntent state,
      final WorkflowInstanceRecord value,
      final ExecutableFlowElement element) {
    if (element != null) {
      value.setElementId(element.getId());
      value.setBpmnElementType(element.getElementType());
    }

    streamWriter.appendFollowUpEvent(key, state, value);

    materializedState.onEventProduced(key, state, value);
  }

  public void appendResolvedIncidentEvent(
      final long incidentKey, final IncidentRecord incidentRecord) {
    streamWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
  }

  public long deferEvent(
      final WorkflowInstanceIntent intent, final WorkflowInstanceRecord recordValue) {
    return deferRecord(recordValue.getFlowScopeKey(), recordValue, intent);
  }

  public long deferRecord(
      long scopeKey, WorkflowInstanceRecord value, WorkflowInstanceIntent intent) {
    final long elementInstanceKey = keyGenerator.nextKey();
    materializedState.deferRecord(elementInstanceKey, scopeKey, value, intent);

    return elementInstanceKey;
  }

  public void removeDeferredEvent(final long scopeKey, final long key) {
    materializedState.removeStoredRecord(scopeKey, key, Purpose.DEFERRED);
  }

  public TypedStreamWriter getStreamWriter() {
    return streamWriter;
  }

  public void setStreamWriter(final TypedStreamWriter streamWriter) {
    this.streamWriter = streamWriter;
  }

  public void storeFailedRecord(
      long key, WorkflowInstanceRecord recordValue, WorkflowInstanceIntent intent) {
    materializedState.storeFailedRecord(key, recordValue, intent);
  }
}
