/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field names and their
 * order are the stored format.
 */
@MsgpackPojo
public final class EventTrigger extends MsgpackBufferValue<EventTrigger> implements DbValue {

  DirectBuffer elementId = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  DirectBuffer variables = Decoding.EMPTY;

  long eventKey;
  long processInstanceKey = -1L;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public EventTrigger() {}

  // Copies over the previous event
  public EventTrigger(final EventTrigger other) {
    this();

    final int length = other.getLength();
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[length]);
    other.write(buffer, 0);
    wrap(buffer, 0, length);
  }

  public DirectBuffer getElementId() {
    return elementId;
  }

  public EventTrigger setElementId(final DirectBuffer elementId) {
    this.elementId = BufferUtil.cloneBuffer(elementId);
    return this;
  }

  public DirectBuffer getVariables() {
    return variables;
  }

  public EventTrigger setVariables(final DirectBuffer variables) {
    this.variables = BufferUtil.cloneBuffer(variables);
    return this;
  }

  public long getEventKey() {
    return eventKey;
  }

  public EventTrigger setEventKey(final long eventKey) {
    this.eventKey = eventKey;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public EventTrigger setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
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
