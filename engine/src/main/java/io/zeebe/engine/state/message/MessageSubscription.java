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

public class MessageSubscription implements DbValue {

  private final DirectBuffer messageName = new UnsafeBuffer();
  private final DirectBuffer correlationKey = new UnsafeBuffer();
  private final DirectBuffer messageVariables = new UnsafeBuffer();

  private long workflowInstanceKey;
  private long elementInstanceKey;
  private long messageKey;
  private long commandSentTime;
  private boolean closeOnCorrelate;

  public MessageSubscription() {}

  public MessageSubscription(
      long workflowInstanceKey,
      long elementInstanceKey,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      boolean closeOnCorrelate) {
    this.workflowInstanceKey = workflowInstanceKey;
    this.elementInstanceKey = elementInstanceKey;

    this.messageName.wrap(messageName);
    this.correlationKey.wrap(correlationKey);
    this.closeOnCorrelate = closeOnCorrelate;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKey;
  }

  public DirectBuffer getMessageVariables() {
    return messageVariables;
  }

  public void setMessageVariables(DirectBuffer variables) {
    this.messageVariables.wrap(variables);
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public long getMessageKey() {
    return messageKey;
  }

  public void setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
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

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelate;
  }

  public void setCloseOnCorrelate(boolean closeOnCorrelate) {
    this.closeOnCorrelate = closeOnCorrelate;
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    this.workflowInstanceKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    this.elementInstanceKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    this.messageKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    this.commandSentTime = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    this.closeOnCorrelate = buffer.getByte(offset) == 1;
    offset += 1;

    offset = readIntoBuffer(buffer, offset, messageName);
    offset = readIntoBuffer(buffer, offset, correlationKey);
    readIntoBuffer(buffer, offset, messageVariables);
  }

  @Override
  public int getLength() {
    return 1
        + Long.BYTES * 4
        + Integer.BYTES * 3
        + messageName.capacity()
        + correlationKey.capacity()
        + messageVariables.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    buffer.putLong(offset, workflowInstanceKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, elementInstanceKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, messageKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, commandSentTime, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putByte(offset, (byte) (closeOnCorrelate ? 1 : 0));
    offset += 1;

    offset = writeIntoBuffer(buffer, offset, messageName);
    offset = writeIntoBuffer(buffer, offset, correlationKey);
    offset = writeIntoBuffer(buffer, offset, messageVariables);
    assert offset == getLength() : "End offset differs with getLength()";
  }
}
