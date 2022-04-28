/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class EventScopeInstance extends UnpackedObject implements DbValue {

  private final BooleanProperty acceptingProp = new BooleanProperty("accepting");
  private final BooleanProperty interruptedProp = new BooleanProperty("interrupted", false);

  private final ArrayProperty<StringValue> interruptingElementIdsProp =
      new ArrayProperty<>("interrupting", new StringValue());

  private final ArrayProperty<StringValue> boundaryElementIdsProp =
      new ArrayProperty<>("boundaryElementIds", new StringValue());

  public EventScopeInstance() {
    declareProperty(acceptingProp)
        .declareProperty(interruptingElementIdsProp)
        .declareProperty(boundaryElementIdsProp)
        .declareProperty(interruptedProp);
  }

  public EventScopeInstance(final EventScopeInstance other) {
    this();

    final int length = other.getLength();
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[length]);
    other.write(buffer, 0);
    wrap(buffer, 0, length);
  }

  public boolean isAccepting() {
    return acceptingProp.getValue();
  }

  public EventScopeInstance setAccepting(final boolean accepting) {
    acceptingProp.setValue(accepting);
    return this;
  }

  public boolean isInterrupted() {
    return interruptedProp.getValue();
  }

  public EventScopeInstance setInterrupted(final boolean interrupted) {
    interruptedProp.setValue(interrupted);
    return this;
  }

  public EventScopeInstance addInterruptingElementId(final DirectBuffer elementId) {
    interruptingElementIdsProp.add().wrap(elementId);
    return this;
  }

  public EventScopeInstance addBoundaryElementId(final DirectBuffer elementId) {
    boundaryElementIdsProp.add().wrap(elementId);
    return this;
  }

  public boolean isInterruptingElementId(final DirectBuffer elementId) {
    for (final StringValue interruptingElementId : interruptingElementIdsProp) {
      if (interruptingElementId.getValue().equals(elementId)) {
        return true;
      }
    }

    return false;
  }

  public boolean isBoundaryElementId(final DirectBuffer elementId) {
    for (final StringValue boundaryElementId : boundaryElementIdsProp) {
      if (boundaryElementId.getValue().equals(elementId)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        acceptingProp, interruptingElementIdsProp, boundaryElementIdsProp, interruptedProp);
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
    return Objects.equals(acceptingProp, that.acceptingProp)
        && Objects.equals(interruptingElementIdsProp, that.interruptingElementIdsProp)
        && Objects.equals(boundaryElementIdsProp, that.boundaryElementIdsProp)
        && Objects.equals(interruptedProp, that.interruptedProp);
  }
}
