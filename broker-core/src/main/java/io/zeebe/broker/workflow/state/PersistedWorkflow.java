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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedWorkflow implements Persistable {
  int version = -1;
  long key = -1;
  final UnsafeBuffer bpmnProcessId = new UnsafeBuffer(0, 0);
  final UnsafeBuffer resourceName = new UnsafeBuffer(0, 0);
  final UnsafeBuffer resource = new UnsafeBuffer(0, 0);

  public PersistedWorkflow() {}

  public PersistedWorkflow(
      DirectBuffer processId,
      DirectBuffer resourceName,
      DirectBuffer resource,
      int version,
      long key) {
    this.bpmnProcessId.wrap(cloneBuffer(processId));
    this.resourceName.wrap(cloneBuffer(resourceName));
    this.resource.wrap(cloneBuffer(resource));

    this.version = version;
    this.key = key;
  }

  public int getVersion() {
    return version;
  }

  public long getKey() {
    return key;
  }

  public UnsafeBuffer getBpmnProcessId() {
    return bpmnProcessId;
  }

  public UnsafeBuffer getResourceName() {
    return resourceName;
  }

  public UnsafeBuffer getResource() {
    return resource;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    int valueOffset = offset;
    version = buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN);
    valueOffset += Integer.BYTES;
    key = buffer.getLong(valueOffset, ByteOrder.LITTLE_ENDIAN);
    valueOffset += Long.BYTES;
    valueOffset = readIntoBuffer(buffer, valueOffset, bpmnProcessId);
    valueOffset = readIntoBuffer(buffer, valueOffset, resourceName);
    readIntoBuffer(buffer, valueOffset, resource);
  }

  @Override
  public int getLength() {
    return Long.BYTES
        + Integer.BYTES
        + Integer.BYTES * 3 // sizes
        + bpmnProcessId.capacity()
        + resourceName.capacity()
        + resource.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    int valueOffset = offset;
    buffer.putInt(offset, version, ByteOrder.LITTLE_ENDIAN);
    valueOffset += Integer.BYTES;
    buffer.putLong(valueOffset, key, ByteOrder.LITTLE_ENDIAN);
    valueOffset += Long.BYTES;
    valueOffset = writeIntoBuffer(buffer, valueOffset, bpmnProcessId);
    valueOffset = writeIntoBuffer(buffer, valueOffset, resourceName);
    valueOffset = writeIntoBuffer(buffer, valueOffset, resource);
    assert (valueOffset - offset) == getLength() : "End offset differs with getLength()";
  }

  public int writeKeyToBuffer(MutableDirectBuffer buffer, int offset) {
    final int keyOffset = writeWorkflowKey(buffer, offset, bpmnProcessId, version);
    assert (keyOffset - offset) == getKeyLength() : "End offset differs with getKeyLength()";
    return keyOffset;
  }

  @Override
  public void writeKey(MutableDirectBuffer keyBuffer, int offset) {
    writeKeyToBuffer(keyBuffer, offset);
  }

  public static int writeWorkflowKey(
      MutableDirectBuffer buffer, int offset, DirectBuffer bpmnProcessId, int version) {
    int keyOffset = offset;
    keyOffset = writeIntoBuffer(buffer, keyOffset, bpmnProcessId);
    buffer.putInt(keyOffset, version, ByteOrder.LITTLE_ENDIAN);
    keyOffset += Integer.BYTES;
    return keyOffset;
  }

  public int getKeyLength() {
    return bpmnProcessId.capacity() + Long.BYTES;
  }

  @Override
  public String toString() {
    return "PersistedWorkflow{"
        + "version="
        + version
        + ", key="
        + key
        + ", bpmnProcessId="
        + bufferAsString(bpmnProcessId)
        + ", resourceName="
        + bufferAsString(resourceName)
        + ", resource="
        + bufferAsString(resource)
        + '}';
  }
}
