/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn;

import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.util.Lists;

public final class WorkflowInstanceAssert
    extends AbstractListAssert<
        WorkflowInstanceAssert,
        List<Record<WorkflowInstanceRecord>>,
        Record<WorkflowInstanceRecord>,
        ObjectAssert<Record<WorkflowInstanceRecord>>> {

  public WorkflowInstanceAssert(final List<Record<WorkflowInstanceRecord>> actual) {
    super(actual, WorkflowInstanceAssert.class);
  }

  @Override
  protected ObjectAssert<Record<WorkflowInstanceRecord>> toAssert(
      final Record<WorkflowInstanceRecord> value, final String description) {
    return new ObjectAssert<>(value).describedAs(description);
  }

  @Override
  protected WorkflowInstanceAssert newAbstractIterableAssert(
      final Iterable<? extends Record<WorkflowInstanceRecord>> iterable) {
    return new WorkflowInstanceAssert(Lists.newArrayList(iterable));
  }

  public static WorkflowInstanceAssert assertThat(
      final List<Record<WorkflowInstanceRecord>> workflowInstanceEvents) {
    return new WorkflowInstanceAssert(workflowInstanceEvents);
  }

  /**
   * Asserts that once an element is in state terminating, no flow-related events in its scope are
   * evaluated anymore
   */
  public WorkflowInstanceAssert doesNotEvaluateFlowAfterTerminatingElement(final String elementId) {
    final DirectBuffer elementIdBuffer = BufferUtil.wrapString(elementId);

    final Optional<Record<WorkflowInstanceRecord>> terminatingRecordOptional =
        actual.stream()
            .filter(
                r ->
                    r.getIntent() == WorkflowInstanceIntent.ELEMENT_TERMINATING
                        && elementIdBuffer.equals(r.getValue().getElementIdBuffer()))
            .findFirst();

    if (!terminatingRecordOptional.isPresent()) {
      failWithMessage(
          "Assumption not met: there is not ELEMENT_TERMINATING record for element %s", elementId);
    }

    final Record terminatingRecord = terminatingRecordOptional.get();
    final long instanceKey = terminatingRecord.getKey();

    final Long2ObjectHashMap<Record<WorkflowInstanceRecord>> recordsByPosition =
        new Long2ObjectHashMap<>();
    actual.forEach(r -> recordsByPosition.put(r.getPosition(), r));

    // - once a terminating record is written, there shall be no record with a greater getPosition
    // that
    //   - was handled (has a follow-up event)
    //   - is in an event in the terminating flow scope
    //   - is a non-terminating event
    final Optional<Record<WorkflowInstanceRecord>> firstViolatingRecord =
        actual.stream()
            .map(r -> (Record) r)
            .filter(r -> r.getSourceRecordPosition() > terminatingRecord.getPosition())
            .map(r -> recordsByPosition.get(r.getSourceRecordPosition()))
            .filter(r -> r.getValue().getFlowScopeKey() == instanceKey)
            .filter(r -> isFlowEvaluatingState(r.getIntent()))
            .findFirst();

    if (firstViolatingRecord.isPresent()) {
      failWithMessage(
          "Record %s should not have a follow-up event as the flow scope was terminating at that point",
          firstViolatingRecord.get());
    }

    return this;
  }

  private static boolean isFlowEvaluatingState(final Intent state) {
    return state == WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN
        || state == WorkflowInstanceIntent.ELEMENT_COMPLETED
        || state == WorkflowInstanceIntent.ELEMENT_ACTIVATING;
  }
}
