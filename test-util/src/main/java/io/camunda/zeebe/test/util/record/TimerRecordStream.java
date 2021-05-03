/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.record;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.TimerRecordValue;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;

public final class TimerRecordStream
    extends ExporterRecordStream<TimerRecordValue, TimerRecordStream> {

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

  public TimerRecordStream withProcessDefinitionKey(final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public TimerRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }
}
