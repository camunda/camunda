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

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceSubscription implements BufferReader, BufferWriter {

  private static final int STATE_OPENING = 0;
  private static final int STATE_OPENED = 1;
  private static final int STATE_CLOSING = 2;

  private final DirectBuffer messageName = new UnsafeBuffer();
  private final DirectBuffer correlationKey = new UnsafeBuffer();

  private long workflowInstanceKey;
  private long elementInstanceKey;
  private int subscriptionPartitionId;
  private long commandSentTime;

  private int state = STATE_OPENING;

  public WorkflowInstanceSubscription() {}

  public WorkflowInstanceSubscription(long workflowInstanceKey, long elementInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.elementInstanceKey = elementInstanceKey;
  }

  public WorkflowInstanceSubscription(
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      final long commandSentTime) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.elementInstanceKey = elementInstanceKey;
    this.commandSentTime = commandSentTime;

    this.messageName.wrap(messageName);
    this.correlationKey.wrap(correlationKey);
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public void setMessageName(DirectBuffer messageName) {
    this.messageName.wrap(messageName);
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public void setCorrelationKey(DirectBuffer correlationKey) {
    this.correlationKey.wrap(correlationKey);
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
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

  public int getSubscriptionPartitionId() {
    return subscriptionPartitionId;
  }

  public void setSubscriptionPartitionId(int subscriptionPartitionId) {
    this.subscriptionPartitionId = subscriptionPartitionId;
  }

  public boolean isOpening() {
    return state == STATE_OPENING;
  }

  public boolean isClosing() {
    return state == STATE_CLOSING;
  }

  public void setOpened() {
    state = STATE_OPENED;
  }

  public void setClosing() {
    state = STATE_CLOSING;
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    this.workflowInstanceKey = buffer.getLong(offset, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    this.elementInstanceKey = buffer.getLong(offset, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    this.subscriptionPartitionId = buffer.getInt(offset, STATE_BYTE_ORDER);
    offset += Integer.BYTES;

    this.commandSentTime = buffer.getLong(offset, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    this.state = buffer.getInt(offset, STATE_BYTE_ORDER);
    offset += Integer.BYTES;

    offset = readIntoBuffer(buffer, offset, messageName);
    readIntoBuffer(buffer, offset, correlationKey);
  }

  @Override
  public int getLength() {
    return Long.BYTES * 3 + Integer.BYTES * 4 + messageName.capacity() + correlationKey.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, workflowInstanceKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, elementInstanceKey, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putInt(offset, subscriptionPartitionId, STATE_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putLong(offset, commandSentTime, STATE_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putInt(offset, state, STATE_BYTE_ORDER);
    offset += Integer.BYTES;

    offset = writeIntoBuffer(buffer, offset, messageName);
    offset = writeIntoBuffer(buffer, offset, correlationKey);
    assert offset == getLength() : "End offset differs with getLength()";
  }

  @Override
  public String toString() {
    return "WorkflowInstanceSubscription{"
        + "elementInstanceKey="
        + elementInstanceKey
        + ", messageName="
        + BufferUtil.bufferAsString(messageName)
        + ", correlationKey="
        + BufferUtil.bufferAsString(correlationKey)
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", subscriptionPartitionId="
        + subscriptionPartitionId
        + ", commandSentTime="
        + commandSentTime
        + ", state="
        + state
        + '}';
  }
}
