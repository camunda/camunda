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
package io.zeebe.broker.task.index;

import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.logstreams.processor.HashIndexSnapshotSupport;
import io.zeebe.broker.task.processor.TaskSubscription;
import io.zeebe.hashindex.Long2BytesHashIndex;
import io.zeebe.logstreams.spi.SnapshotSupport;

/**
 * Index that maps <b>task instance key</b> to
 *
 * <li> state
 * <li> lock owner length
 * <li> lock owner (max 64 chars)
 */
public class TaskInstanceIndex
{
    private static final int INDEX_VALUE_SIZE = SIZE_OF_SHORT + SIZE_OF_INT + SIZE_OF_CHAR * TaskSubscription.LOCK_OWNER_MAX_LENGTH;

    private static final int STATE_OFFSET = 0;
    private static final int LOCK_OWNER_LENGTH_OFFSET = STATE_OFFSET + SIZE_OF_SHORT;
    private static final int LOCK_OWNER_OFFSET = LOCK_OWNER_LENGTH_OFFSET + SIZE_OF_INT;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final byte[] rawBuffer = new byte[INDEX_VALUE_SIZE];
    private final UnsafeBuffer buffer = new UnsafeBuffer(rawBuffer);
    private final UnsafeBuffer lockOwnerBuffer = new UnsafeBuffer(0, 0);

    private final Long2BytesHashIndex index;
    private final HashIndexSnapshotSupport<Long2BytesHashIndex> snapshotSupport;

    private long key;
    private boolean isRead = false;

    public TaskInstanceIndex()
    {
        this.index = new Long2BytesHashIndex(8388608, 8, INDEX_VALUE_SIZE);
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

    public TaskInstanceIndex wrapTaskInstanceKey(long key)
    {
        this.isRead = index.get(key, rawBuffer);
        this.key = key;

        return this;
    }

    public short getState()
    {
        return isRead ? buffer.getShort(STATE_OFFSET, BYTE_ORDER) : -1;
    }

    public DirectBuffer getLockOwner()
    {
        if (isRead)
        {
            final int length = buffer.getInt(LOCK_OWNER_LENGTH_OFFSET, BYTE_ORDER);
            lockOwnerBuffer.wrap(buffer, LOCK_OWNER_OFFSET, length);
        }
        else
        {
            lockOwnerBuffer.wrap(0, 0);
        }
        return lockOwnerBuffer;
    }

    public TaskInstanceIndex newTaskInstance(long taskInstanceKey)
    {
        key = taskInstanceKey;
        isRead = true;
        return this;
    }

    public void write()
    {
        ensureRead();
        index.put(key, buffer.byteArray());
    }

    public TaskInstanceIndex setState(short state)
    {
        ensureRead();
        buffer.putShort(STATE_OFFSET, state, BYTE_ORDER);
        return this;
    }

    public TaskInstanceIndex setLockOwner(DirectBuffer lockOwner)
    {
        ensureRead();
        buffer.putInt(LOCK_OWNER_LENGTH_OFFSET, lockOwner.capacity(), BYTE_ORDER);
        buffer.putBytes(LOCK_OWNER_OFFSET, lockOwner, 0, lockOwner.capacity());
        return this;
    }

    private void ensureRead()
    {
        if (!isRead)
        {
            throw new IllegalStateException("must call wrapTaskInstanceKey() before");
        }
    }

    public void close()
    {
        index.close();
    }

}
