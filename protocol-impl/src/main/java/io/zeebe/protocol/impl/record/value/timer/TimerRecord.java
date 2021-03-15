/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.timer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.TimerRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class TimerRecord extends UnifiedRecordValue implements TimerRecordValue {

  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey");
  private final LongProperty dueDateProp = new LongProperty("dueDate");
  private final StringProperty targetElementId = new StringProperty("targetElementId");
  private final IntegerProperty repetitionsProp = new IntegerProperty("repetitions");
  private final LongProperty processDefinitionKeyProp = new LongProperty("processDefinitionKey");

  public TimerRecord() {
    declareProperty(elementInstanceKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(dueDateProp)
        .declareProperty(targetElementId)
        .declareProperty(repetitionsProp)
        .declareProperty(processDefinitionKeyProp);
  }

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return targetElementId.getValue();
  }

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public TimerRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  @Override
  public long getDueDate() {
    return dueDateProp.getValue();
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(targetElementId.getValue());
  }

  @Override
  public int getRepetitions() {
    return repetitionsProp.getValue();
  }

  public TimerRecord setRepetitions(final int repetitions) {
    repetitionsProp.setValue(repetitions);
    return this;
  }

  public TimerRecord setTargetElementId(final DirectBuffer targetElementId) {
    this.targetElementId.setValue(targetElementId);
    return this;
  }

  public TimerRecord setDueDate(final long dueDate) {
    dueDateProp.setValue(dueDate);
    return this;
  }

  public TimerRecord setElementInstanceKey(final long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  public TimerRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }
}
