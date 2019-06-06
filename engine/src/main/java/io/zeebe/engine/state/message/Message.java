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
package io.zeebe.engine.state.message;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class Message implements DbValue {

  private final DirectBuffer name = new UnsafeBuffer();
  private final DirectBuffer correlationKey = new UnsafeBuffer();
  private final DirectBuffer variables = new UnsafeBuffer();
  private final DirectBuffer id = new UnsafeBuffer();
  private long key;
  private long timeToLive;
  private long deadline;

  public Message() {}

  public Message(
      long key,
      DirectBuffer name,
      DirectBuffer correlationKey,
      DirectBuffer variables,
      DirectBuffer id,
      long timeToLive,
      long deadline) {
    this.name.wrap(name);
    this.correlationKey.wrap(correlationKey);
    this.variables.wrap(variables);
    this.id.wrap(id);

    this.key = key;
    this.timeToLive = timeToLive;
    this.deadline = deadline;
  }

  public DirectBuffer getName() {
    return name;
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public DirectBuffer getVariables() {
    return variables;
  }

  public DirectBuffer getId() {
    return id;
  }

  public long getTimeToLive() {
    return timeToLive;
  }

  public long getDeadline() {
    return deadline;
  }

  public long getKey() {
    return key;
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    offset = readIntoBuffer(buffer, offset, name);
    offset = readIntoBuffer(buffer, offset, correlationKey);
    offset = readIntoBuffer(buffer, offset, variables);
    offset = readIntoBuffer(buffer, offset, id);

    timeToLive = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;
    deadline = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;
    key = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
  }

  @Override
  public int getLength() {
    return name.capacity()
        + correlationKey.capacity()
        + variables.capacity()
        + id.capacity()
        + Integer.BYTES * 4
        + Long.BYTES * 3;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    int valueOffset = offset;
    valueOffset = writeIntoBuffer(buffer, valueOffset, name);
    valueOffset = writeIntoBuffer(buffer, valueOffset, correlationKey);
    valueOffset = writeIntoBuffer(buffer, valueOffset, variables);
    valueOffset = writeIntoBuffer(buffer, valueOffset, id);

    buffer.putLong(valueOffset, timeToLive, ZB_DB_BYTE_ORDER);
    valueOffset += Long.BYTES;
    buffer.putLong(valueOffset, deadline, ZB_DB_BYTE_ORDER);
    valueOffset += Long.BYTES;
    buffer.putLong(valueOffset, key, ZB_DB_BYTE_ORDER);
    valueOffset += Long.BYTES;
    assert (valueOffset - offset) == getLength() : "End offset differs with getLength()";
  }
}
