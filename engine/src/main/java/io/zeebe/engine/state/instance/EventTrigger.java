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
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class EventTrigger extends UnpackedObject implements DbValue {

  private final StringProperty elementIdProp = new StringProperty("elementId");
  private final BinaryProperty variablesProp = new BinaryProperty("variables");
  private final LongProperty eventKeyProp = new LongProperty("eventKey");

  public EventTrigger() {
    this.declareProperty(elementIdProp)
        .declareProperty(variablesProp)
        .declareProperty(eventKeyProp);
  }

  // Copies over the previous event
  public EventTrigger(EventTrigger other) {
    this();

    final int length = other.getLength();
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[length]);
    other.write(buffer, 0);
    this.wrap(buffer, 0, length);
  }

  public DirectBuffer getElementId() {
    return elementIdProp.getValue();
  }

  public EventTrigger setElementId(DirectBuffer elementId) {
    this.elementIdProp.setValue(elementId);
    return this;
  }

  public DirectBuffer getVariables() {
    return variablesProp.getValue();
  }

  public EventTrigger setVariables(DirectBuffer variables) {
    this.variablesProp.setValue(variables);
    return this;
  }

  public long getEventKey() {
    return eventKeyProp.getValue();
  }

  public EventTrigger setEventKey(long eventKey) {
    this.eventKeyProp.setValue(eventKey);
    return this;
  }

  @Override
  public boolean equals(Object o) {
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

  @Override
  public int hashCode() {
    return Objects.hash(getElementId(), getVariables());
  }
}
