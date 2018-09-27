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

import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.broker.subscription.message.state.Subscription;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WorkflowSubscription implements Subscription {

  private final DirectBuffer messageName = new UnsafeBuffer();
  private final DirectBuffer correlationKey = new UnsafeBuffer();

  private long workflowInstanceKey;
  private long activityInstanceKey;
  private long commandSentTime;

  private boolean isOpen;

  public WorkflowSubscription() {}

  public WorkflowSubscription(
      long workflowInstanceKey, long activityInstanceKey, DirectBuffer messageName) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.activityInstanceKey = activityInstanceKey;

    this.messageName.wrap(messageName);
  }

  public WorkflowSubscription(
      long workflowInstanceKey,
      long activityInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.activityInstanceKey = activityInstanceKey;

    this.messageName.wrap(messageName);
    this.correlationKey.wrap(correlationKey);
  }

  WorkflowSubscription(
      final String messageName,
      final String correlationKey,
      final long workflowInstanceKey,
      final long activityInstanceKey,
      final long commandSentTime) {
    this(
        workflowInstanceKey,
        activityInstanceKey,
        new UnsafeBuffer(messageName.getBytes()),
        new UnsafeBuffer(correlationKey.getBytes()));

    setCommandSentTime(commandSentTime);
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
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

  public boolean isNotOpen() {
    return !isOpen;
  }

  public void setOpen(boolean open) {
    isOpen = open;
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    this.workflowInstanceKey = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    this.activityInstanceKey = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    this.commandSentTime = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    this.isOpen = buffer.getByte(offset) == 1;
    offset += Byte.BYTES;

    offset = readIntoBuffer(buffer, offset, messageName);
    readIntoBuffer(buffer, offset, correlationKey);
  }

  @Override
  public int getLength() {
    return Long.BYTES * 3
        + Byte.BYTES
        + Integer.BYTES * 2
        + messageName.capacity()
        + correlationKey.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, workflowInstanceKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    buffer.putLong(offset, activityInstanceKey, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    buffer.putLong(offset, commandSentTime, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    buffer.putByte(offset, (byte) (isOpen ? 1 : 0));
    offset += Byte.BYTES;

    offset = writeIntoBuffer(buffer, offset, messageName);
    offset = writeIntoBuffer(buffer, offset, correlationKey);
    assert offset == getLength() : "End offset differs with getLength()";
  }

  public void writeCommandSentTime(MutableDirectBuffer keyBuffer, int offset) {
    keyBuffer.putLong(offset, commandSentTime, ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public int getKeyLength() {
    return 2 * Long.BYTES + messageName.capacity();
  }

  @Override
  public void writeKey(MutableDirectBuffer keyBuffer, int offset) {
    final int startOffset = offset;
    keyBuffer.putLong(offset, workflowInstanceKey);
    offset += Long.BYTES;
    keyBuffer.putLong(offset, activityInstanceKey);
    offset += Long.BYTES;
    final int nameLength = messageName.capacity();
    keyBuffer.putBytes(offset, messageName.byteArray(), 0, nameLength);
    offset += nameLength;

    assert (offset - startOffset) == getKeyLength()
        : "Offset problem: offset is not equal to expected key length";
  }
}
