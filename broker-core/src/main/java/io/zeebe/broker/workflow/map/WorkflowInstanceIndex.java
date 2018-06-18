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
package io.zeebe.broker.workflow.map;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.map.iterator.Long2BytesZbMapEntry;
import java.nio.ByteOrder;
import java.util.Iterator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Maps <b>workflow instance key</b> to
 * <li>workflow instance event position
 * <li>workflow key
 * <li>active token count
 * <li>activity instance key
 */
public class WorkflowInstanceIndex implements AutoCloseable {
  private static final int INDEX_VALUE_SIZE =
      SIZE_OF_LONG + SIZE_OF_LONG + SIZE_OF_INT + SIZE_OF_LONG;

  private static final int POSITION_OFFSET = 0;
  private static final int WORKFLOW_KEY_OFFSET = POSITION_OFFSET + SIZE_OF_LONG;
  private static final int TOKEN_COUNT_OFFSET = WORKFLOW_KEY_OFFSET + SIZE_OF_LONG;
  private static final int ACTIVITY_INSTANCE_KEY_OFFSET = TOKEN_COUNT_OFFSET + SIZE_OF_INT;

  private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

  private final WorkflowInstance workflowInstance = new WorkflowInstance();
  private final WorkflowInstanceIterator iterator = new WorkflowInstanceIterator();

  private final Long2BytesZbMap map;

  public WorkflowInstanceIndex() {
    this.map = new Long2BytesZbMap(INDEX_VALUE_SIZE);
  }

  public Long2BytesZbMap getMap() {
    return map;
  }

  public void remove(long workflowInstanceKey) {
    map.remove(workflowInstanceKey);
  }

  public WorkflowInstance get(long key) {
    final DirectBuffer currentValue = map.get(key);
    if (currentValue != null) {
      workflowInstance.wrap(key, currentValue);
      return workflowInstance;
    } else {
      return null;
    }
  }

  public WorkflowInstance newWorkflowInstance(long workflowInstanceKey) {
    workflowInstance.reset(workflowInstanceKey);
    return workflowInstance;
  }

  public Iterator<WorkflowInstance> iterator() {
    iterator.reset();
    return iterator;
  }

  @Override
  public void close() {
    map.close();
  }

  public class WorkflowInstanceIterator implements Iterator<WorkflowInstance> {
    private Iterator<Long2BytesZbMapEntry> iterator;
    private WorkflowInstance workflowInstance = new WorkflowInstance();

    public void reset() {
      iterator = map.iterator();
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public WorkflowInstance next() {
      final Long2BytesZbMapEntry entry = iterator.next();
      workflowInstance.wrap(entry.getKey(), entry.getValue());

      return workflowInstance;
    }
  }

  public class WorkflowInstance {
    private long workflowInstanceKey;
    private final UnsafeBuffer currentValue = new UnsafeBuffer(new byte[INDEX_VALUE_SIZE]);

    public void reset(long workflowInstanceKey) {
      this.workflowInstanceKey = workflowInstanceKey;
      // ensure that all properties are set before saving the new entry
    }

    public void wrap(long workflowInstanceKey, DirectBuffer value) {
      this.workflowInstanceKey = workflowInstanceKey;
      this.currentValue.putBytes(0, value, 0, value.capacity());
    }

    public long getKey() {
      return workflowInstanceKey;
    }

    public long getPosition() {
      return currentValue.getLong(POSITION_OFFSET, BYTE_ORDER);
    }

    public int getTokenCount() {
      return currentValue.getInt(TOKEN_COUNT_OFFSET, BYTE_ORDER);
    }

    public long getActivityInstanceKey() {
      return currentValue.getLong(ACTIVITY_INSTANCE_KEY_OFFSET, BYTE_ORDER);
    }

    public long getWorkflowKey() {
      return currentValue.getLong(WORKFLOW_KEY_OFFSET, BYTE_ORDER);
    }

    public WorkflowInstance setPosition(long position) {
      currentValue.putLong(POSITION_OFFSET, position, BYTE_ORDER);
      return this;
    }

    public WorkflowInstance setWorkflowKey(long workflowKey) {
      currentValue.putLong(WORKFLOW_KEY_OFFSET, workflowKey, BYTE_ORDER);
      return this;
    }

    public WorkflowInstance setActivityInstanceKey(long activityInstanceKey) {
      currentValue.putLong(ACTIVITY_INSTANCE_KEY_OFFSET, activityInstanceKey, BYTE_ORDER);
      return this;
    }

    public WorkflowInstance setActiveTokenCount(int activeTokenCount) {
      currentValue.putInt(TOKEN_COUNT_OFFSET, activeTokenCount, BYTE_ORDER);
      return this;
    }

    public void write() {
      map.put(workflowInstanceKey, currentValue);
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("WorkflowInstance [key=");
      builder.append(getKey());
      builder.append(", position=");
      builder.append(getPosition());
      builder.append(", tokenCount=");
      builder.append(getTokenCount());
      builder.append(", activityInstanceKey=");
      builder.append(getActivityInstanceKey());
      builder.append(", workflowKey=");
      builder.append(getWorkflowKey());
      builder.append("]");
      return builder.toString();
    }
  }
}
