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

import static org.agrona.BitUtil.*;

import java.nio.ByteOrder;

import io.zeebe.broker.workflow.graph.transformer.BpmnTransformer;
import io.zeebe.hashindex.Long2BytesHashIndex;
import io.zeebe.logstreams.snapshot.HashIndexSnapshotSupport;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Index that maps <b>activity instance key</b> to
 *
 * <li>task instance key
 * <li>activity id length
 * <li>activity id (max 255 chars)
 */
public class ActivityInstanceIndex implements AutoCloseable
{
    private static final int SIZE_OF_ACTIVITY_ID = BpmnTransformer.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int INDEX_VALUE_SIZE = SIZE_OF_LONG + SIZE_OF_INT + SIZE_OF_ACTIVITY_ID;

    private static final int TASK_KEY_OFFSET = 0;
    private static final int ACTIVITY_ID_LENGTH_OFFSET = TASK_KEY_OFFSET + SIZE_OF_LONG;
    private static final int ACTIVITY_ID_OFFSET = ACTIVITY_ID_LENGTH_OFFSET + SIZE_OF_INT;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final byte[] rawBuffer = new byte[INDEX_VALUE_SIZE];
    private final UnsafeBuffer buffer = new UnsafeBuffer(rawBuffer);
    private final UnsafeBuffer activityIdBuffer = new UnsafeBuffer(new byte[SIZE_OF_ACTIVITY_ID]);

    private final Long2BytesHashIndex index;
    private final HashIndexSnapshotSupport<Long2BytesHashIndex> snapshotSupport;

    private long key;
    private boolean isRead = false;

    public ActivityInstanceIndex()
    {
        this.index = new Long2BytesHashIndex(Short.MAX_VALUE, 256, INDEX_VALUE_SIZE);
        this.snapshotSupport = new HashIndexSnapshotSupport<>(index);
    }

    public HashIndexSnapshotSupport getSnapshotSupport()
    {
        return snapshotSupport;
    }

    public void reset()
    {
        isRead = false;
    }

    public void remove(long activityInstanceKey)
    {
        index.remove(activityInstanceKey, rawBuffer);
    }

    public ActivityInstanceIndex wrapActivityInstanceKey(long key)
    {
        this.isRead = index.get(key, rawBuffer);
        this.key = key;

        return this;
    }

    public long getTaskKey()
    {
        return isRead ? buffer.getLong(TASK_KEY_OFFSET, BYTE_ORDER) : -1L;
    }

    public DirectBuffer getActivityId()
    {
        if (isRead)
        {
            final int length = buffer.getInt(ACTIVITY_ID_LENGTH_OFFSET, BYTE_ORDER);

            activityIdBuffer.wrap(buffer, ACTIVITY_ID_OFFSET, length);
        }
        else
        {
            activityIdBuffer.wrap(0, 0);
        }
        return activityIdBuffer;
    }

    public ActivityInstanceIndex newActivityInstance(long activityInstanceKey)
    {
        key = activityInstanceKey;
        isRead = true;
        return this;
    }

    public void write()
    {
        ensureRead();
        index.put(key, buffer.byteArray());
    }

    public ActivityInstanceIndex setActivityId(DirectBuffer activityId)
    {
        ensureRead();
        buffer.putInt(ACTIVITY_ID_LENGTH_OFFSET, activityId.capacity(), BYTE_ORDER);
        buffer.putBytes(ACTIVITY_ID_OFFSET, activityId, 0, activityId.capacity());
        return this;
    }

    public ActivityInstanceIndex setTaskKey(long taskKey)
    {
        ensureRead();
        buffer.putLong(TASK_KEY_OFFSET, taskKey, BYTE_ORDER);
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
