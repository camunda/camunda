/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.db.DbValue;
import io.zeebe.engine.processor.workflow.WorkflowInstanceLifecycle;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class ElementInstance implements DbValue {

  private final IndexedRecord elementRecord;

  private long parentKey = -1;
  private int childCount;
  private long jobKey;
  private int activeTokens = 0;

  private int multiInstanceLoopCounter = 0;
  private long interruptingEventKey = -1;

  private long calledChildInstanceKey = -1L;

  ElementInstance() {
    elementRecord = new IndexedRecord();
  }

  public ElementInstance(
      final long key,
      final ElementInstance parent,
      final WorkflowInstanceIntent state,
      final WorkflowInstanceRecord value) {
    elementRecord = new IndexedRecord(key, state, value);
    parentKey = parent.getKey();
    parent.childCount++;
  }

  public ElementInstance(
      final long key, final WorkflowInstanceIntent state, final WorkflowInstanceRecord value) {
    elementRecord = new IndexedRecord(key, state, value);
  }

  public long getKey() {
    return elementRecord.getKey();
  }

  public WorkflowInstanceIntent getState() {
    return elementRecord.getState();
  }

  public void setState(final WorkflowInstanceIntent state) {
    elementRecord.setState(state);
  }

  public WorkflowInstanceRecord getValue() {
    return elementRecord.getValue();
  }

  public void setValue(final WorkflowInstanceRecord value) {
    elementRecord.setValue(value);
  }

  public long getJobKey() {
    return jobKey;
  }

  public void setJobKey(final long jobKey) {
    this.jobKey = jobKey;
  }

  public void decrementChildCount() {
    childCount--;

    if (childCount < 0) {
      throw new IllegalStateException(
          String.format("Expected the child count to be positive but was %d", childCount));
    }
  }

  public void incrementChildCount() {
    childCount++;
  }

  public boolean canTerminate() {
    return WorkflowInstanceLifecycle.canTerminate(getState());
  }

  public boolean isActive() {
    return WorkflowInstanceLifecycle.isActive(getState());
  }

  public boolean isTerminating() {
    return WorkflowInstanceLifecycle.isTerminating(getState());
  }

  public boolean isInFinalState() {
    return WorkflowInstanceLifecycle.isFinalState(getState());
  }

  public void spawnToken() {
    activeTokens += 1;
  }

  public void consumeToken() {
    activeTokens -= 1;

    if (activeTokens < 0) {
      throw new IllegalStateException(
          String.format("Expected the active token count to be positive but was %d", activeTokens));
    }
  }

  public int getNumberOfActiveTokens() {
    return activeTokens;
  }

  public int getNumberOfActiveElementInstances() {
    return childCount;
  }

  public int getNumberOfActiveExecutionPaths() {
    return activeTokens + childCount;
  }

  public int getMultiInstanceLoopCounter() {
    return multiInstanceLoopCounter;
  }

  public void setMultiInstanceLoopCounter(final int loopCounter) {
    multiInstanceLoopCounter = loopCounter;
  }

  public void incrementMultiInstanceLoopCounter() {
    multiInstanceLoopCounter += 1;
  }

  public long getCalledChildInstanceKey() {
    return calledChildInstanceKey;
  }

  public void setCalledChildInstanceKey(long calledChildInstanceKey) {
    this.calledChildInstanceKey = calledChildInstanceKey;
  }

  public long getInterruptingEventKey() {
    return interruptingEventKey;
  }

  public void setInterruptingEventKey(long key) {
    this.interruptingEventKey = key;
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    final int startOffset = offset;
    childCount = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    jobKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    activeTokens = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    parentKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    offset = readIntoBuffer(buffer, offset, elementRecord);

    multiInstanceLoopCounter = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    calledChildInstanceKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    interruptingEventKey = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    assert (offset - startOffset) == length : "End offset differs from length";
  }

  @Override
  public int getLength() {
    return 4 * Long.BYTES + 4 * Integer.BYTES + elementRecord.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    final int startOffset = offset;

    buffer.putInt(offset, childCount, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putLong(offset, jobKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putInt(offset, activeTokens, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putLong(offset, parentKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    offset = writeIntoBuffer(buffer, offset, elementRecord);

    buffer.putInt(offset, multiInstanceLoopCounter, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putLong(offset, calledChildInstanceKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putLong(offset, interruptingEventKey, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    assert (offset - startOffset) == getLength() : "End offset differs from getLength()";
  }

  public long getParentKey() {
    return parentKey;
  }

  @Override
  public String toString() {
    return "ElementInstance{"
        + "elementRecord="
        + elementRecord
        + ", parentKey="
        + parentKey
        + ", childCount="
        + childCount
        + ", jobKey="
        + jobKey
        + ", activeTokens="
        + activeTokens
        + ", multiInstanceLoopCounter="
        + multiInstanceLoopCounter
        + ", calledChildInstanceKey="
        + calledChildInstanceKey
        + ", interruptingEventKey="
        + interruptingEventKey
        + '}';
  }
}
