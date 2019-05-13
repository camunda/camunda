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
package io.zeebe.engine.state.instance;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.db.DbValue;
import io.zeebe.engine.processor.workflow.WorkflowInstanceLifecycle;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class ElementInstance implements DbValue {

  private final IndexedRecord elementRecord;

  private long parentKey = -1;
  private int childCount;
  private long jobKey;
  private int activeTokens = 0;

  ElementInstance() {
    this.elementRecord = new IndexedRecord();
  }

  public ElementInstance(
      long key,
      ElementInstance parent,
      WorkflowInstanceIntent state,
      WorkflowInstanceRecord value) {
    this.elementRecord = new IndexedRecord(key, state, value);
    parentKey = parent.getKey();
    parent.childCount++;
  }

  public ElementInstance(long key, WorkflowInstanceIntent state, WorkflowInstanceRecord value) {
    this.elementRecord = new IndexedRecord(key, state, value);
  }

  public long getKey() {
    return elementRecord.getKey();
  }

  public WorkflowInstanceIntent getState() {
    return elementRecord.getState();
  }

  public void setState(WorkflowInstanceIntent state) {
    this.elementRecord.setState(state);
  }

  public WorkflowInstanceRecord getValue() {
    return elementRecord.getValue();
  }

  public void setValue(WorkflowInstanceRecord value) {
    this.elementRecord.setValue(value);
  }

  public long getJobKey() {
    return jobKey;
  }

  public void setJobKey(long jobKey) {
    this.jobKey = jobKey;
  }

  public void decrementChildCount() {
    childCount--;
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

  public void spawnToken() {
    this.activeTokens += 1;
  }

  public void consumeToken() {
    this.activeTokens -= 1;
  }

  public int getNumberOfActiveTokens() {
    return activeTokens;
  }

  public int getNumberOfActiveElementInstances() {
    return childCount;
  }

  public int getNumberOfActiveExecutionPaths() {
    return activeTokens + getNumberOfActiveElementInstances();
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
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

    assert (offset - startOffset) == length : "End offset differs from length";
  }

  @Override
  public int getLength() {
    return 2 * Long.BYTES + 3 * Integer.BYTES + elementRecord.getLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
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
        + '}';
  }
}
