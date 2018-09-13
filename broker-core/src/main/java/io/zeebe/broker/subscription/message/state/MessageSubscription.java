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

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MessageSubscription implements BufferReader, BufferWriter {
  static final int KEY_LENGTH = 3 * Long.BYTES + Integer.BYTES;

  private final DirectBuffer messageName = new UnsafeBuffer();
  private final DirectBuffer correlationKey = new UnsafeBuffer();
  private final DirectBuffer messagePayload = new UnsafeBuffer();

  private int workflowInstancePartitionId;
  private long workflowInstanceKey;
  private long activityInstanceKey;
  private long commandSentTime;

  public MessageSubscription() {}

  public MessageSubscription(
      int workflowInstancePartitionId,
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey) {
    this.workflowInstancePartitionId = workflowInstancePartitionId;
    this.workflowInstanceKey = workflowInstanceKey;
    this.activityInstanceKey = activityInstanceKey;

    this.messageName.wrap(messageName);
    this.correlationKey.wrap(correlationKey);
  }

  MessageSubscription(
      final String messageName,
      final String correlationKey,
      final String messagePayload,
      final int partitionId,
      final long workflowInstanceKey,
      final long activityInstanceKey,
      final long commandSentTime) {
    this(
        partitionId,
        workflowInstanceKey,
        activityInstanceKey,
        new UnsafeBuffer(messageName.getBytes()),
        new UnsafeBuffer(correlationKey.getBytes()));

    setCommandSentTime(commandSentTime);
    setMessagePayload(new UnsafeBuffer(messagePayload.getBytes()));
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

  public int getWorkflowInstancePartitionId() {
    return workflowInstancePartitionId;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public long getActivityInstanceKey() {
    return activityInstanceKey;
  }

  public long getCommandSentTime() {
    return commandSentTime;
  }

  public void setCommandSentTime(long commandSentTime) {
    this.commandSentTime = commandSentTime;
  }

  public void setPayload(DirectBuffer payload) {
    this.messagePayload.wrap(payload);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    this.workflowInstancePartitionId = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Integer.BYTES;

    this.workflowInstanceKey = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    this.activityInstanceKey = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    this.commandSentTime = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    offset = Message.readIntoBuffer(buffer, offset, messageName);
    offset = Message.readIntoBuffer(buffer, offset, correlationKey);
    Message.readIntoBuffer(buffer, offset, messagePayload);
  }

  @Override
  public int getLength() {
    return Long.BYTES * 3
        + Integer.BYTES * 4
        + messageName.capacity()
        + correlationKey.capacity()
        + messagePayload.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    buffer.putInt(offset, workflowInstancePartitionId, ByteOrder.LITTLE_ENDIAN);
    offset += Integer.BYTES;

    buffer.putLong(offset, workflowInstanceKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    buffer.putLong(offset, activityInstanceKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    buffer.putLong(offset, commandSentTime, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    offset = Message.writeIntoBuffer(buffer, offset, messageName);
    offset = Message.writeIntoBuffer(buffer, offset, correlationKey);
    offset = Message.writeIntoBuffer(buffer, offset, messagePayload);
    assert offset == getLength() : "End offset differs with getLength()";
  }

  public void writeKey(MutableDirectBuffer keyBuffer, int offset) {
    keyBuffer.putLong(offset, commandSentTime, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;
    keyBuffer.putInt(offset, getWorkflowInstancePartitionId());
    offset += Integer.BYTES;
    keyBuffer.putLong(offset, workflowInstanceKey);
    offset += Long.BYTES;
    keyBuffer.putLong(offset, activityInstanceKey);
    offset += Long.BYTES;

    assert offset == KEY_LENGTH : "Offset problem: offset is not equal to expected key length";
  }

  public int getKeyLength() {
    return KEY_LENGTH;
  }

  public void setMessagePayload(DirectBuffer payload) {
    this.messagePayload.wrap(payload);
  }
}
