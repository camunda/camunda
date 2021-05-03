/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class EventTrigger extends UnpackedObject implements DbValue {

  private final StringProperty elementIdProp = new StringProperty("elementId");
  private final BinaryProperty variablesProp = new BinaryProperty("variables");
  private final LongProperty eventKeyProp = new LongProperty("eventKey");

  public EventTrigger() {
    declareProperty(elementIdProp).declareProperty(variablesProp).declareProperty(eventKeyProp);
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
