/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.TimerRecordValue;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;

public class TimerRecordStream extends ExporterRecordStream<TimerRecordValue, TimerRecordStream> {

  public TimerRecordStream(final Stream<Record<TimerRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected TimerRecordStream supply(final Stream<Record<TimerRecordValue>> wrappedStream) {
    return new TimerRecordStream(wrappedStream);
  }

  public TimerRecordStream withElementInstanceKey(final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public TimerRecordStream withDueDate(final long dueDate) {
    return valueFilter(v -> v.getDueDate() == dueDate);
  }

  public TimerRecordStream withHandlerNodeId(final String handlerNodeId) {
    return valueFilter(v -> v.getTargetElementId().equals(handlerNodeId));
  }

  public TimerRecordStream withHandlerNodeId(final DirectBuffer handlerNodeId) {
    return withHandlerNodeId(bufferAsString(handlerNodeId));
  }

  public TimerRecordStream withRepetitions(final int repetitions) {
    return valueFilter(v -> v.getRepetitions() == repetitions);
  }

  public TimerRecordStream withWorkflowKey(final long workflowKey) {
    return valueFilter(v -> v.getWorkflowKey() == workflowKey);
  }

  public TimerRecordStream withWorkflowInstanceKey(final long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }
}
