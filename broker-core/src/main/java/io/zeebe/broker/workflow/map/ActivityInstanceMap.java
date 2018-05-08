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

import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.map.Long2BytesZbMap;
import io.zeebe.model.bpmn.impl.ZeebeConstraints;

/**
 * Maps <b>activity instance key</b> to
 *
 * <li>job instance key
 * <li>activity id length
 * <li>activity id (max 255 chars)
 */
public class ActivityInstanceMap implements AutoCloseable
{
    private static final int SIZE_OF_ACTIVITY_ID = ZeebeConstraints.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int INDEX_VALUE_SIZE = SIZE_OF_LONG + SIZE_OF_INT + SIZE_OF_ACTIVITY_ID;

    private static final int JOB_KEY_OFFSET = 0;
    private static final int ACTIVITY_ID_LENGTH_OFFSET = JOB_KEY_OFFSET + SIZE_OF_LONG;
    private static final int ACTIVITY_ID_OFFSET = ACTIVITY_ID_LENGTH_OFFSET + SIZE_OF_INT;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[INDEX_VALUE_SIZE]);
    private final UnsafeBuffer activityIdBuffer = new UnsafeBuffer(new byte[SIZE_OF_ACTIVITY_ID]);

    private final Long2BytesZbMap map;

    private long key;
    private boolean isRead = false;

    public ActivityInstanceMap()
    {
        this.map = new Long2BytesZbMap(INDEX_VALUE_SIZE);
    }

    public Long2BytesZbMap getMap()
    {
        return map;
    }

    public void reset()
    {
        isRead = false;
    }

    public void remove(long activityInstanceKey)
    {
        map.remove(activityInstanceKey);
    }

    public ActivityInstanceMap wrapActivityInstanceKey(long key)
    {
        final DirectBuffer result = map.get(key);
        if (result != null)
        {
            this.buffer.putBytes(0, result, 0, result.capacity());
        }

        this.isRead = result != null;
        this.key = key;

        return this;
    }

    public long getJobKey()
    {
        return isRead ? buffer.getLong(JOB_KEY_OFFSET, BYTE_ORDER) : -1L;
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

    public ActivityInstanceMap newActivityInstance(long activityInstanceKey)
    {
        key = activityInstanceKey;
        isRead = true;
        return this;
    }

    public void write()
    {
        ensureRead();
        map.put(key, buffer);
    }

    public ActivityInstanceMap setActivityId(DirectBuffer activityId)
    {
        ensureRead();
        buffer.putInt(ACTIVITY_ID_LENGTH_OFFSET, activityId.capacity(), BYTE_ORDER);
        buffer.putBytes(ACTIVITY_ID_OFFSET, activityId, 0, activityId.capacity());
        return this;
    }

    public ActivityInstanceMap setJobKey(long jobKey)
    {
        ensureRead();
        buffer.putLong(JOB_KEY_OFFSET, jobKey, BYTE_ORDER);
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
        map.close();
    }
}
