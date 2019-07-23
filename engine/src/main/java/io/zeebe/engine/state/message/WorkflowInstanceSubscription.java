/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
  private final DirectBuffer targetElementId = new UnsafeBuffer();

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
      DirectBuffer targetElementId,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      long commandSentTime,
      boolean closeOnCorrelate) {
    this(workflowInstanceKey, elementInstanceKey);

    this.targetElementId.wrap(targetElementId);
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

  public DirectBuffer getTargetElementId() {
    return targetElementId;
  }

  public void setTargetElementId(DirectBuffer targetElementId) {
    this.targetElementId.wrap(targetElementId);
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
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
    offset = readIntoBuffer(buffer, offset, targetElementId);

    assert (offset - startOffset) == length : "End offset differs from length";
  }

  @Override
  public int getLength() {
    return 1
        + Long.BYTES * 3
        + Integer.BYTES * 5
        + messageName.capacity()
        + correlationKey.capacity()
        + targetElementId.capacity();
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
    offset = writeIntoBuffer(buffer, offset, targetElementId);
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
