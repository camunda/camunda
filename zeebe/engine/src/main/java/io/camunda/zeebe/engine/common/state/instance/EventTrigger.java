/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class EventTrigger extends UnpackedObject implements DbValue {

  private final StringProperty elementIdProp = new StringProperty("elementId");
  private final BinaryProperty variablesProp = new BinaryProperty("variables");
  private final LongProperty eventKeyProp = new LongProperty("eventKey");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);

  public EventTrigger() {
    super(4);
    declareProperty(elementIdProp)
        .declareProperty(variablesProp)
        .declareProperty(eventKeyProp)
        .declareProperty(processInstanceKeyProp);
  }

  // Copies over the previous event
  public EventTrigger(final EventTrigger other) {
    this();

    final int length = other.getLength();
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[length]);
    other.write(buffer, 0);
    wrap(buffer, 0, length);
  }

  public DirectBuffer getElementId() {
    return elementIdProp.getValue();
  }

  public EventTrigger setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public DirectBuffer getVariables() {
    return variablesProp.getValue();
  }

  public EventTrigger setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  public long getEventKey() {
    return eventKeyProp.getValue();
  }

  public EventTrigger setEventKey(final long eventKey) {
    eventKeyProp.setValue(eventKey);
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public EventTrigger setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getElementId(), getVariables());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EventTrigger that = (EventTrigger) o;
    return BufferUtil.equals(getElementId(), that.getElementId())
        && BufferUtil.equals(getVariables(), that.getVariables());
  }
}
