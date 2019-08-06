/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.BooleanProperty;
import io.zeebe.msgpack.value.StringValue;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class EventScopeInstance extends UnpackedObject implements DbValue {

  private final BooleanProperty acceptingProp = new BooleanProperty("accepting");
  private final ArrayProperty<StringValue> interruptingProp =
      new ArrayProperty<>("interrupting", new StringValue());

  public EventScopeInstance() {
    this.declareProperty(acceptingProp).declareProperty(interruptingProp);
  }

  public EventScopeInstance(EventScopeInstance other) {
    this();

    final int length = other.getLength();
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[length]);
    other.write(buffer, 0);
    this.wrap(buffer, 0, length);
  }

  public boolean isAccepting() {
    return acceptingProp.getValue();
  }

  public EventScopeInstance setAccepting(boolean accepting) {
    this.acceptingProp.setValue(accepting);
    return this;
  }

  public EventScopeInstance addInterrupting(DirectBuffer elementId) {
    this.interruptingProp.add().wrap(elementId);
    return this;
  }

  public boolean isInterrupting(DirectBuffer elementId) {
    for (StringValue stringValue : interruptingProp) {
      if (stringValue.getValue().equals(elementId)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(acceptingProp, interruptingProp);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EventScopeInstance)) {
      return false;
    }
    final EventScopeInstance that = (EventScopeInstance) o;
    return Objects.equals(acceptingProp, that.acceptingProp)
        && Objects.equals(interruptingProp, that.interruptingProp);
  }
}
