/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn;

import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.util.Lists;

public final class ProcessInstanceAssert
    extends AbstractListAssert<
        ProcessInstanceAssert,
        List<Record<ProcessInstanceRecord>>,
        Record<ProcessInstanceRecord>,
        ObjectAssert<Record<ProcessInstanceRecord>>> {

  public ProcessInstanceAssert(final List<Record<ProcessInstanceRecord>> actual) {
    super(actual, ProcessInstanceAssert.class);
  }

  @Override
  protected ObjectAssert<Record<ProcessInstanceRecord>> toAssert(
      final Record<ProcessInstanceRecord> value, final String description) {
    return new ObjectAssert<>(value).describedAs(description);
  }

  @Override
  protected ProcessInstanceAssert newAbstractIterableAssert(
      final Iterable<? extends Record<ProcessInstanceRecord>> iterable) {
    return new ProcessInstanceAssert(Lists.newArrayList(iterable));
  }

  public static ProcessInstanceAssert assertThat(
      final List<Record<ProcessInstanceRecord>> processInstanceEvents) {
    return new ProcessInstanceAssert(processInstanceEvents);
  }

  /**
   * Asserts that once an element is in state terminating, no flow-related events in its scope are
   * evaluated anymore
   */
  public ProcessInstanceAssert doesNotEvaluateFlowAfterTerminatingElement(final String elementId) {
    final DirectBuffer elementIdBuffer = BufferUtil.wrapString(elementId);

    final Optional<Record<ProcessInstanceRecord>> terminatingRecordOptional =
        actual.stream()
            .filter(
                r ->
                    r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING
                        && elementIdBuffer.equals(r.getValue().getElementIdBuffer()))
            .findFirst();

    if (!terminatingRecordOptional.isPresent()) {
      failWithMessage(
          "Assumption not met: there is not ELEMENT_TERMINATING record for element %s", elementId);
    }

    final Record terminatingRecord = terminatingRecordOptional.get();
    final long instanceKey = terminatingRecord.getKey();

    final Long2ObjectHashMap<Record<ProcessInstanceRecord>> recordsByPosition =
        new Long2ObjectHashMap<>();
    actual.forEach(r -> recordsByPosition.put(r.getPosition(), r));

    // - once a terminating record is written, there shall be no record with a greater getPosition
    // that
    //   - was handled (has a follow-up event)
    //   - is in an event in the terminating flow scope
    //   - is a non-terminating event
    final Optional<Record<ProcessInstanceRecord>> firstViolatingRecord =
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
    return state == ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN
        || state == ProcessInstanceIntent.ELEMENT_COMPLETED
        || state == ProcessInstanceIntent.ELEMENT_ACTIVATING;
  }
}
