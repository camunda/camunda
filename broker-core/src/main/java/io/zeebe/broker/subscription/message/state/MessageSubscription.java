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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MessageSubscription implements Subscription {
  static final int KEY_LENGTH = 2 * Long.BYTES;

  private final DirectBuffer messageName = new UnsafeBuffer();
  private final DirectBuffer correlationKey = new UnsafeBuffer();
  private final DirectBuffer messagePayload = new UnsafeBuffer();

  private long workflowInstanceKey;
  private long activityInstanceKey;
  private long commandSentTime;

  public MessageSubscription() {}

  public MessageSubscription(long workflowInstanceKey, long activityInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.activityInstanceKey = activityInstanceKey;
  }

  public MessageSubscription(
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.activityInstanceKey = activityInstanceKey;

    this.messageName.wrap(messageName);
    this.correlationKey.wrap(correlationKey);
  }

  MessageSubscription(
      final String messageName,
      final String correlationKey,
      final String messagePayload,
      final long workflowInstanceKey,
      final long activityInstanceKey,
      final long commandSentTime) {
    this(
        workflowInstanceKey,
        activityInstanceKey,
        new UnsafeBuffer(messageName.getBytes()),
        new UnsafeBuffer(correlationKey.getBytes()));

    setCommandSentTime(commandSentTime);
    setMessagePayload(new UnsafeBuffer(messagePayload.getBytes()));
  }

  @Override
  public DirectBuffer getMessageName() {
    return messageName;
  }

  @Override
  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public DirectBuffer getMessagePayload() {
    return messagePayload;
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

  @Override
  public void setCommandSentTime(long commandSentTime) {
    this.commandSentTime = commandSentTime;
  }

  public void setPayload(DirectBuffer payload) {
    this.messagePayload.wrap(payload);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    this.workflowInstanceKey = buffer.getLong(offset, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    this.activityInstanceKey = buffer.getLong(offset, STATE_BYTE_ORDER);
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

    buffer.putLong(offset, activityInstanceKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, commandSentTime, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    offset = writeIntoBuffer(buffer, offset, messageName);
    offset = writeIntoBuffer(buffer, offset, correlationKey);
    offset = writeIntoBuffer(buffer, offset, messagePayload);
    assert offset == getLength() : "End offset differs with getLength()";
  }

  @Override
  public void writeKey(MutableDirectBuffer keyBuffer, int offset) {
    final int startOffset = offset;
    keyBuffer.putLong(offset, workflowInstanceKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;
    keyBuffer.putLong(offset, activityInstanceKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    assert (offset - startOffset) == KEY_LENGTH
        : "Offset problem: offset is not equal to expected key length";
  }

  @Override
  public void writeCommandSentTime(MutableDirectBuffer keyBuffer, int offset) {
    keyBuffer.putLong(offset, commandSentTime, STATE_BYTE_ORDER);
  }

  @Override
  public int getKeyLength() {
    return KEY_LENGTH;
  }

  public void setMessagePayload(DirectBuffer payload) {
    this.messagePayload.wrap(payload);
  }
}
