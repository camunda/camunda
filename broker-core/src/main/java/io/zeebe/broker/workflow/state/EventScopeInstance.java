/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.state;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BooleanProperty;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class EventScopeInstance extends UnpackedObject implements DbValue {

  private BooleanProperty acceptingProp = new BooleanProperty("accepting");
  private BooleanProperty interruptingProp = new BooleanProperty("interrupting");

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

  public EventScopeInstance setInterrupting(boolean interrupting) {
    this.interruptingProp.setValue(interrupting);
    return this;
  }

  public boolean isInterrupting() {
    return interruptingProp.getValue();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EventScopeInstance instance = (EventScopeInstance) o;
    return Objects.equals(isAccepting(), instance.isAccepting())
        && Objects.equals(isInterrupting(), instance.isInterrupting());
  }

  @Override
  public int hashCode() {
    return Objects.hash(isAccepting(), isInterrupting());
  }
}
