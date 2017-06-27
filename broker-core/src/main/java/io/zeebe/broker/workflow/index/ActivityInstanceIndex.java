/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.workflow.index;

import static org.agrona.BitUtil.SIZE_OF_CHAR;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.logstreams.processor.HashIndexSnapshotSupport;
import io.zeebe.broker.workflow.graph.transformer.BpmnTransformer;
import io.zeebe.hashindex.Long2BytesHashIndex;
import io.zeebe.logstreams.spi.SnapshotSupport;

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

    public SnapshotSupport getSnapshotSupport()
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
