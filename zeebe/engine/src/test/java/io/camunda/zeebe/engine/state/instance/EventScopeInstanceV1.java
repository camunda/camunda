/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class EventScopeInstanceV1 extends UnpackedObject implements DbValue {

  private final BooleanProperty acceptingProp = new BooleanProperty("accepting");
  private final BooleanProperty interruptedProp = new BooleanProperty("interrupted", false);

  private final ArrayProperty<StringValue> interruptingElementIdsProp =
      new ArrayProperty<>("interrupting", StringValue::new);

  private final ArrayProperty<StringValue> boundaryElementIdsProp =
      new ArrayProperty<>("boundaryElementIds", StringValue::new);

  public EventScopeInstanceV1() {
    super(4);
    declareProperty(acceptingProp)
        .declareProperty(interruptingElementIdsProp)
        .declareProperty(boundaryElementIdsProp)
        .declareProperty(interruptedProp);
  }

  public EventScopeInstanceV1(final EventScopeInstanceV1 other) {
    this();

    final int length = other.getLength();
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[length]);
    other.write(buffer, 0);
    wrap(buffer, 0, length);
  }

  public boolean isAccepting() {
    return acceptingProp.getValue();
  }

  public EventScopeInstanceV1 setAccepting(final boolean accepting) {
    acceptingProp.setValue(accepting);
    return this;
  }

  public boolean isInterrupted() {
    return interruptedProp.getValue();
  }

  public EventScopeInstanceV1 setInterrupted(final boolean interrupted) {
    interruptedProp.setValue(interrupted);
    return this;
  }

  public EventScopeInstanceV1 addInterruptingElementId(final DirectBuffer elementId) {
    interruptingElementIdsProp.add().wrap(elementId);
    return this;
  }

  public EventScopeInstanceV1 addBoundaryElementId(final DirectBuffer elementId) {
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
    if (!(o instanceof EventScopeInstanceV1)) {
      return false;
    }
    final EventScopeInstanceV1 that = (EventScopeInstanceV1) o;
    return Objects.equals(acceptingProp, that.acceptingProp)
        && Objects.equals(interruptingElementIdsProp, that.interruptingElementIdsProp)
        && Objects.equals(boundaryElementIdsProp, that.boundaryElementIdsProp)
        && Objects.equals(interruptedProp, that.interruptedProp);
  }
}
