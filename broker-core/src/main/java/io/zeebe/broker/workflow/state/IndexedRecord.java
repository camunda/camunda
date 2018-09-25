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

import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.BufferWriter;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class IndexedRecord implements BufferWriter, BufferReader {

  private long key;
  private WorkflowInstanceIntent state;

  private final WorkflowInstanceRecord value = new WorkflowInstanceRecord();

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
    key = buffer.getLong(offset, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    final short stateIdx = buffer.getShort(offset, ByteOrder.LITTLE_ENDIAN);
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

    buffer.putLong(offset, key, ByteOrder.LITTLE_ENDIAN);
    offset += Long.BYTES;

    buffer.putShort(offset, state.value(), ByteOrder.LITTLE_ENDIAN);
    offset += Short.BYTES;

    assert (offset - startOffset) == getLength() - value.getLength()
        : "End offset differs with getLength()";
    value.write(buffer, offset);
  }
}
