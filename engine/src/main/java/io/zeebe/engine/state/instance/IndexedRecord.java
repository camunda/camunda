/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.instance;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;

import io.zeebe.db.DbValue;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class IndexedRecord implements DbValue {

  private final WorkflowInstanceRecord value = new WorkflowInstanceRecord();
  private long key;
  private WorkflowInstanceIntent state;

  IndexedRecord() {}

  public IndexedRecord(
      long key, WorkflowInstanceIntent instanceState, WorkflowInstanceRecord record) {
    this.key = key;
    this.state = instanceState;
    setValue(record);
  }

  public long getKey() {
    return key;
  }

  public WorkflowInstanceIntent getState() {
    return state;
  }

  public void setState(WorkflowInstanceIntent state) {
    this.state = state;
  }

  public WorkflowInstanceRecord getValue() {
    return value;
  }

  public void setValue(WorkflowInstanceRecord value) {
    final MutableDirectBuffer valueBuffer = new UnsafeBuffer(0, 0);
    final int encodedLength = value.getLength();
    valueBuffer.wrap(new byte[encodedLength]);

    value.write(valueBuffer, 0);
    this.value.wrap(valueBuffer, 0, encodedLength);
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    final int startOffset = offset;
    key = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    final short stateIdx = buffer.getShort(offset, ZB_DB_BYTE_ORDER);
    state = WorkflowInstanceIntent.values()[stateIdx];
    offset += Short.BYTES;

    final int currentLength = offset - startOffset;
    final DirectBuffer clonedBuffer =
        BufferUtil.cloneBuffer(buffer, offset, length - currentLength);
    value.wrap(clonedBuffer);
  }

  @Override
  public int getLength() {
    return Long.BYTES + Short.BYTES + value.getLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    final int startOffset = offset;

    buffer.putLong(offset, key, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    buffer.putShort(offset, state.value(), ZB_DB_BYTE_ORDER);
    offset += Short.BYTES;

    assert (offset - startOffset) == getLength() - value.getLength()
        : "End offset differs with getLength()";
    value.write(buffer, offset);
  }

  @Override
  public String toString() {
    return "IndexedRecord{" + "key=" + key + ", state=" + state + ", value=" + value + '}';
  }
}
