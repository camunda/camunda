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
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceSubscription implements DbValue {

  private static final int STATE_OPENING = 0;
  private static final int STATE_OPENED = 1;
  private static final int STATE_CLOSING = 2;

  private final DirectBuffer messageName = new UnsafeBuffer();
  private final DirectBuffer correlationKey = new UnsafeBuffer();
  private final DirectBuffer handlerNodeId = new UnsafeBuffer();

  private long workflowInstanceKey;
  private long elementInstanceKey;
  private int subscriptionPartitionId;
  private long commandSentTime;
  private boolean closeOnCorrelate = true;

  private int state = STATE_OPENING;

  public WorkflowInstanceSubscription() {}

  public WorkflowInstanceSubscription(long workflowInstanceKey, long elementInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.elementInstanceKey = elementInstanceKey;
  }

  public WorkflowInstanceSubscription(
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer handlerNodeId,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      long commandSentTime,
      boolean closeOnCorrelate) {
    this(workflowInstanceKey, elementInstanceKey);

    this.handlerNodeId.wrap(handlerNodeId);
    this.commandSentTime = commandSentTime;
    this.messageName.wrap(messageName);
    this.correlationKey.wrap(correlationKey);
    this.closeOnCorrelate = closeOnCorrelate;
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

  public DirectBuffer getHandlerNodeId() {
    return handlerNodeId;
  }

  public void setHandlerNodeId(DirectBuffer handlerNodeId) {
    this.handlerNodeId.wrap(handlerNodeId);
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

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelate;
  }

  public void setCloseOnCorrelate(boolean closeOnCorrelate) {
    this.closeOnCorrelate = closeOnCorrelate;
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
    final int startOffset = offset;
    this.workflowInstanceKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    this.elementInstanceKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    this.subscriptionPartitionId = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    this.commandSentTime = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    this.state = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    this.closeOnCorrelate = buffer.getByte(offset) == 1;
    offset += 1;

    offset = readIntoBuffer(buffer, offset, messageName);
    offset = readIntoBuffer(buffer, offset, correlationKey);
    offset = readIntoBuffer(buffer, offset, handlerNodeId);

    assert (offset - startOffset) == length : "End offset differs from length";
  }

  @Override
  public int getLength() {
    return 1
        + Long.BYTES * 3
        + Integer.BYTES * 5
        + messageName.capacity()
        + correlationKey.capacity()
        + handlerNodeId.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, workflowInstanceKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, elementInstanceKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putInt(offset, subscriptionPartitionId, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putLong(offset, commandSentTime, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putInt(offset, state, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putByte(offset, (byte) (closeOnCorrelate ? 1 : 0));
    offset += 1;

    offset = writeIntoBuffer(buffer, offset, messageName);
    offset = writeIntoBuffer(buffer, offset, correlationKey);
    offset = writeIntoBuffer(buffer, offset, handlerNodeId);
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
