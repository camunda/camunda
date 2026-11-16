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
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class EventScopeInstance extends MsgpackBufferValue<EventScopeInstance>
    implements DbValue {

  @Nullable Boolean accepting;

  @MsgpackProperty("interrupting")
  final List<DirectBuffer> interruptingElementIds = new ArrayList<>();

  final List<DirectBuffer> boundaryElementIds = new ArrayList<>();
  boolean interrupted = false;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public EventScopeInstance() {}

  public EventScopeInstance(final EventScopeInstance other) {
    this();

    final int length = other.getLength();
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[length]);
    other.write(buffer, 0);
    wrap(buffer, 0, length);
  }

  public boolean isAccepting() {
    return Decoding.required(accepting, "accepting");
  }

  public EventScopeInstance setAccepting(final boolean accepting) {
    this.accepting = accepting;
    return this;
  }

  public boolean isInterrupted() {
    return interrupted;
  }

  public EventScopeInstance setInterrupted(final boolean interrupted) {
    this.interrupted = interrupted;
    return this;
  }

  public EventScopeInstance addInterruptingElementId(final DirectBuffer elementId) {
    interruptingElementIds.add(elementId);
    return this;
  }

  public EventScopeInstance addBoundaryElementId(final DirectBuffer elementId) {
    boundaryElementIds.add(elementId);
    return this;
  }

  public boolean isInterruptingElementId(final DirectBuffer elementId) {
    for (final DirectBuffer interruptingElementId : interruptingElementIds) {
      if (interruptingElementId.equals(elementId)) {
        return true;
      }
    }

    return false;
  }

  public boolean isBoundaryElementId(final DirectBuffer elementId) {
    for (final DirectBuffer boundaryElementId : boundaryElementIds) {
      if (boundaryElementId.equals(elementId)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(accepting, interruptingElementIds, boundaryElementIds, interrupted);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EventScopeInstance)) {
      return false;
    }
    final EventScopeInstance that = (EventScopeInstance) o;
    return isAccepting() == that.isAccepting()
        && interruptingElementIds.equals(that.interruptingElementIds)
        && boundaryElementIds.equals(that.boundaryElementIds)
        && isInterrupted() == that.isInterrupted();
  }

  @Override
  protected MsgpackCodec<EventScopeInstance> codec() {
    return EventScopeInstanceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<EventScopeInstance> ops() {
    return EventScopeInstanceOps.INSTANCE;
  }
}
