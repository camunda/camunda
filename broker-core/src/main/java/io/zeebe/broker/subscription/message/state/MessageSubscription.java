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
package io.zeebe.broker.subscription.message.state;

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MessageSubscription implements BufferReader, BufferWriter {

  private final DirectBuffer messageName = new UnsafeBuffer();
  private final DirectBuffer correlationKey = new UnsafeBuffer();
  private final DirectBuffer messagePayload = new UnsafeBuffer();

  private long workflowInstanceKey;
  private long elementInstanceKey;
  private long commandSentTime;

  public MessageSubscription() {}

  public MessageSubscription(
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.elementInstanceKey = elementInstanceKey;

    this.messageName.wrap(messageName);
    this.correlationKey.wrap(correlationKey);
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setMessageName(DirectBuffer messageName) {
    this.messageName.wrap(messageName);
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public DirectBuffer getMessagePayload() {
    return messagePayload;
  }

  public void setMessagePayload(DirectBuffer payload) {
    this.messagePayload.wrap(payload);
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public long getCommandSentTime() {
    return commandSentTime;
  }

  public void setCommandSentTime(long commandSentTime) {
    this.commandSentTime = commandSentTime;
  }

  public boolean isCorrelating() {
    return commandSentTime > 0;
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    this.workflowInstanceKey = buffer.getLong(offset, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    this.elementInstanceKey = buffer.getLong(offset, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    this.commandSentTime = buffer.getLong(offset, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    offset = readIntoBuffer(buffer, offset, messageName);
    offset = readIntoBuffer(buffer, offset, correlationKey);
    readIntoBuffer(buffer, offset, messagePayload);
  }

  @Override
  public int getLength() {
    return Long.BYTES * 3
        + Integer.BYTES * 3
        + messageName.capacity()
        + correlationKey.capacity()
        + messagePayload.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, workflowInstanceKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, elementInstanceKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, commandSentTime, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    offset = writeIntoBuffer(buffer, offset, messageName);
    offset = writeIntoBuffer(buffer, offset, correlationKey);
    offset = writeIntoBuffer(buffer, offset, messagePayload);
    assert offset == getLength() : "End offset differs with getLength()";
  }
}
