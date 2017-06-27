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
package io.zeebe.broker.incident.index;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BitUtil.SIZE_OF_SHORT;

import java.nio.ByteOrder;

import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.logstreams.processor.HashIndexSnapshotSupport;
import io.zeebe.hashindex.Long2BytesHashIndex;
import io.zeebe.logstreams.spi.SnapshotSupport;

/**
 * Index that maps <b>incident key</b> to
 *
 * <li>incident state
 * <li>incident event position
 * <li>failure event position
 */
public class IncidentIndex
{
    private static final int STATE_OFFSET = 0;
    private static final int INCIDENT_EVENT_POSITION_OFFSET = STATE_OFFSET + SIZE_OF_SHORT;
    private static final int FAILURE_EVENT_POSITION_OFFSET = INCIDENT_EVENT_POSITION_OFFSET + SIZE_OF_LONG;

    private static final int INDEX_VALUE_SIZE = SIZE_OF_SHORT + 2 * SIZE_OF_LONG;

    private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private final byte[] rawBuffer = new byte[INDEX_VALUE_SIZE];
    private final UnsafeBuffer buffer = new UnsafeBuffer(rawBuffer);

    private final Long2BytesHashIndex index;
    private final HashIndexSnapshotSupport<Long2BytesHashIndex> snapshotSupport;

    private long key;
    private boolean isRead = false;

    public IncidentIndex()
    {
        this.index = new Long2BytesHashIndex(Short.MAX_VALUE, 64, INDEX_VALUE_SIZE);
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

    public void remove(long incidentKey)
    {
        index.remove(incidentKey, rawBuffer);
    }

    public IncidentIndex wrapIncidentKey(long key)
    {
        this.isRead = index.get(key, rawBuffer);
        this.key = key;

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

    private void ensureRead()
    {
        if (!isRead)
        {
            throw new IllegalStateException("must call wrap() before");
        }
    }

    public void close()
    {
        index.close();
    }

}
