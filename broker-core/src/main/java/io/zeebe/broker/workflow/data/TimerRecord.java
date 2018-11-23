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
package io.zeebe.broker.workflow.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class TimerRecord extends UnpackedObject {

  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final LongProperty dueDateProp = new LongProperty("dueDate");
  private final StringProperty handlerNodeId = new StringProperty("handlerNodeId");
  private final IntegerProperty repetitionsProp = new IntegerProperty("repetitions");

  public TimerRecord() {
    this.declareProperty(elementInstanceKeyProp)
        .declareProperty(dueDateProp)
        .declareProperty(handlerNodeId)
        .declareProperty(repetitionsProp);
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public TimerRecord setElementInstanceKey(long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  public long getDueDate() {
    return dueDateProp.getValue();
  }

  public TimerRecord setDueDate(long dueDate) {
    this.dueDateProp.setValue(dueDate);
    return this;
  }

  public DirectBuffer getHandlerNodeId() {
    return handlerNodeId.getValue();
  }

  public TimerRecord setHandlerNodeId(DirectBuffer handlerNodeId) {
    this.handlerNodeId.setValue(handlerNodeId);
    return this;
  }

  public int getRepetitions() {
    return repetitionsProp.getValue();
  }

  public TimerRecord setRepetitions(int repetitions) {
    this.repetitionsProp.setValue(repetitions);
    return this;
  }
}
