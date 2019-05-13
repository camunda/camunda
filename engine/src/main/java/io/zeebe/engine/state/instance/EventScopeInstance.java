/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

  public EventScopeInstance setAccepting(boolean accepting) {
    this.acceptingProp.setValue(accepting);
    return this;
  }

  public boolean isAccepting() {
    return acceptingProp.getValue();
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

  @Override
  public int hashCode() {
    return Objects.hash(acceptingProp, interruptingProp);
  }
}
