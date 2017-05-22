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
package org.camunda.tngp.broker.incident.index;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import java.nio.ByteOrder;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.processor.HashIndexSnapshotSupport;
import org.camunda.tngp.hashindex.Long2BytesHashIndex;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;

/**
 * Index that maps <b>incident key</b> to
 *
 * <li>incident state
 * <li>incident event position
 * <li>failure event position
 * <li>channel id
 * <li>connection id
 * <li>request id
 */
public class IncidentIndex
{
    private static final int STATE_OFFSET = 0;
    private static final int INCIDENT_EVENT_POSITION_OFFSET = STATE_OFFSET + SIZE_OF_SHORT;
    private static final int FAILURE_EVENT_POSITION_OFFSET = INCIDENT_EVENT_POSITION_OFFSET + SIZE_OF_LONG;
    private static final int CHANNEL_ID_OFFSET = FAILURE_EVENT_POSITION_OFFSET + SIZE_OF_LONG;
    private static final int CONNECTION_ID_OFFSET = CHANNEL_ID_OFFSET + SIZE_OF_INT;
    private static final int REQUEST_ID_OFFSET = CONNECTION_ID_OFFSET + SIZE_OF_LONG;

    private static final int INDEX_VALUE_SIZE = SIZE_OF_SHORT + SIZE_OF_INT + 4 * SIZE_OF_LONG;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[INDEX_VALUE_SIZE]);

    private final Long2BytesHashIndex index;
    private final HashIndexSnapshotSupport<Long2BytesHashIndex> snapshotSupport;

    private long key;
    private boolean isRead = false;

    public IncidentIndex(final IndexStore indexStore)
    {
        this.index = new Long2BytesHashIndex(indexStore, Short.MAX_VALUE, 64, INDEX_VALUE_SIZE);
        this.snapshotSupport = new HashIndexSnapshotSupport<>(index, indexStore);
    }

    public SnapshotSupport getSnapshotSupport()
    {
        return snapshotSupport;
    }

    public void reset()
    {
        isRead = false;
    }

    public void remove(long incidentKey)
    {
        index.remove(incidentKey);
    }

    public IncidentIndex wrapIncidentKey(long key)
    {
        isRead = false;

        final byte[] indexValue = index.get(key);
        if (indexValue != null)
        {
            buffer.wrap(indexValue);
            isRead = true;
        }

        return this;
    }

    public short getState()
    {
        return isRead ? buffer.getShort(STATE_OFFSET, BYTE_ORDER) : -1;
    }

    public long getIncidentEventPosition()
    {
        return isRead ? buffer.getLong(INCIDENT_EVENT_POSITION_OFFSET, BYTE_ORDER) : -1L;
    }

    public long getFailureEventPosition()
    {
        return isRead ? buffer.getLong(FAILURE_EVENT_POSITION_OFFSET, BYTE_ORDER) : -1L;
    }

    public int getChannelId()
    {
        return isRead ? buffer.getInt(CHANNEL_ID_OFFSET, BYTE_ORDER) : -1;
    }

    public long getConnectionId()
    {
        return isRead ? buffer.getLong(CONNECTION_ID_OFFSET, BYTE_ORDER) : -1L;
    }

    public long getRequestId()
    {
        return isRead ? buffer.getLong(REQUEST_ID_OFFSET, BYTE_ORDER) : -1L;
    }

    public IncidentIndex newIncident(long incidentKey)
    {
        key = incidentKey;
        isRead = true;
        return this;
    }

    public void write()
    {
        ensureRead();
        index.put(key, buffer.byteArray());
    }

    public IncidentIndex setState(short state)
    {
        ensureRead();
        buffer.putShort(STATE_OFFSET, state, BYTE_ORDER);
        return this;
    }

    public IncidentIndex setIncidentEventPosition(long position)
    {
        ensureRead();
        buffer.putLong(INCIDENT_EVENT_POSITION_OFFSET, position, BYTE_ORDER);
        return this;
    }

    public IncidentIndex setFailureEventPosition(long position)
    {
        ensureRead();
        buffer.putLong(FAILURE_EVENT_POSITION_OFFSET, position, BYTE_ORDER);
        return this;
    }

    public IncidentIndex setChannelId(int channelId)
    {
        ensureRead();
        buffer.putInt(CHANNEL_ID_OFFSET, channelId, BYTE_ORDER);
        return this;
    }

    public IncidentIndex setConnectionId(long requestId)
    {
        ensureRead();
        buffer.putLong(CONNECTION_ID_OFFSET, requestId, BYTE_ORDER);
        return this;
    }

    public IncidentIndex setRequestId(long connectionId)
    {
        ensureRead();
        buffer.putLong(REQUEST_ID_OFFSET, connectionId, BYTE_ORDER);
        return this;
    }

    private void ensureRead()
    {
        if (!isRead)
        {
            throw new IllegalStateException("must call wrap() before");
        }
    }

}
