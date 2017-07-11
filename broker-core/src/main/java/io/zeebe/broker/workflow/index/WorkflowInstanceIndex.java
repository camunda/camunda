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
package io.zeebe.broker.workflow.index;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import java.nio.ByteOrder;

import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.logstreams.processor.HashIndexSnapshotSupport;
import io.zeebe.hashindex.Long2BytesHashIndex;
import io.zeebe.logstreams.spi.SnapshotSupport;

/**
 * Index that maps <b>workflow instance key</b> to
 *
 * <li>workflow instance event position
 * <li>active token count
 * <li>activity instance key
 */
public class WorkflowInstanceIndex implements AutoCloseable
{
    private static final int INDEX_VALUE_SIZE = SIZE_OF_LONG + SIZE_OF_INT + SIZE_OF_LONG;

    private static final int POSITION_OFFSET = 0;
    private static final int TOKEN_COUNT_OFFSET = POSITION_OFFSET + SIZE_OF_LONG;
    private static final int ACTIVITY_INSTANCE_KEY_OFFSET = TOKEN_COUNT_OFFSET + SIZE_OF_INT;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final byte[] rawBuffer = new byte[INDEX_VALUE_SIZE];
    private final UnsafeBuffer buffer = new UnsafeBuffer(rawBuffer);

    private final Long2BytesHashIndex index;
    private final HashIndexSnapshotSupport<Long2BytesHashIndex> snapshotSupport;

    private long key;
    private boolean isRead = false;

    public WorkflowInstanceIndex()
    {
        this.index = new Long2BytesHashIndex(Short.MAX_VALUE, 256, INDEX_VALUE_SIZE);
        this.snapshotSupport = new HashIndexSnapshotSupport<>(index);
    }

    public SnapshotSupport getSnapshotSupport()
    {
        return snapshotSupport;
    }

    public void reset()
    {
        isRead = false;
    }

    public void remove(long workflowInstanceKey)
    {
        index.remove(workflowInstanceKey, rawBuffer);
    }

    public WorkflowInstanceIndex wrapWorkflowInstanceKey(long key)
    {
        this.isRead = index.get(key, rawBuffer);
        this.key = key;

        return this;
    }

    public long getPosition()
    {
        return isRead ? buffer.getLong(POSITION_OFFSET, BYTE_ORDER) : -1L;
    }

    public int getTokenCount()
    {
        return isRead ? buffer.getInt(TOKEN_COUNT_OFFSET, BYTE_ORDER) : -1;
    }

    public long getActivityInstanceKey()
    {
        return isRead ? buffer.getLong(ACTIVITY_INSTANCE_KEY_OFFSET, BYTE_ORDER) : -1L;
    }

    public WorkflowInstanceIndex newWorkflowInstance(long workflowInstanceKey)
    {
        key = workflowInstanceKey;
        isRead = true;
        return this;
    }

    public void write()
    {
        ensureRead();
        index.put(key, buffer.byteArray());
    }

    public WorkflowInstanceIndex setPosition(long position)
    {
        ensureRead();
        buffer.putLong(POSITION_OFFSET, position, BYTE_ORDER);
        return this;
    }

    public WorkflowInstanceIndex setActivityKey(long activityInstanceKey)
    {
        ensureRead();
        buffer.putLong(ACTIVITY_INSTANCE_KEY_OFFSET, activityInstanceKey, BYTE_ORDER);
        return this;
    }

    public WorkflowInstanceIndex setActiveTokenCount(int activeTokenCount)
    {
        ensureRead();
        buffer.putInt(TOKEN_COUNT_OFFSET, activeTokenCount, BYTE_ORDER);
        return this;
    }

    private void ensureRead()
    {
        if (!isRead)
        {
            throw new IllegalStateException("must call wrap() before");
        }
    }

    @Override
    public void close()
    {
        index.close();
    }
}
