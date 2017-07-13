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
