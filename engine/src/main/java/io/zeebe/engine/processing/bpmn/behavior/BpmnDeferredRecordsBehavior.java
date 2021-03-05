/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.List;

public final class BpmnDeferredRecordsBehavior {

  private final MutableElementInstanceState elementInstanceState;

  public BpmnDeferredRecordsBehavior(final ZeebeState zeebeState) {
    elementInstanceState = zeebeState.getElementInstanceState();
  }

  public void deferNewRecord(
      final BpmnElementContext context,
      final long key,
      final ProcessInstanceRecord value,
      final ProcessInstanceIntent state) {

    elementInstanceState.storeRecord(
        key, context.getElementInstanceKey(), value, state, Purpose.DEFERRED);
  }

  public List<IndexedRecord> getDeferredRecords(final BpmnElementContext context) {
    return elementInstanceState.getDeferredRecords(context.getElementInstanceKey());
  }

  public void removeDeferredRecord(
      final BpmnElementContext context, final IndexedRecord deferredRecord) {
    elementInstanceState.removeStoredRecord(
        context.getElementInstanceKey(), deferredRecord.getKey(), Purpose.DEFERRED);
  }
}
