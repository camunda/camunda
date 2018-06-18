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

import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.map.Long2BytesZbMap;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Maps <b>incident key</b> to
 * <li>incident state
 * <li>incident event position
 * <li>failure event position
 */
public class IncidentMap {
  private static final int STATE_OFFSET = 0;
  private static final int INCIDENT_EVENT_POSITION_OFFSET = STATE_OFFSET + SIZE_OF_SHORT;
  private static final int FAILURE_EVENT_POSITION_OFFSET =
      INCIDENT_EVENT_POSITION_OFFSET + SIZE_OF_LONG;

  private static final int INDEX_VALUE_SIZE = SIZE_OF_SHORT + 2 * SIZE_OF_LONG;

  private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[INDEX_VALUE_SIZE]);

  private final Long2BytesZbMap map;
  private final ZbMapSnapshotSupport<Long2BytesZbMap> snapshotSupport;

  private long key;
  private boolean isRead = false;

  public IncidentMap() {
    this.map = new Long2BytesZbMap(INDEX_VALUE_SIZE);
    this.snapshotSupport = new ZbMapSnapshotSupport<>(map);
  }

  public Long2BytesZbMap getMap() {
    return map;
  }

  public void reset() {
    isRead = false;
  }

  public void remove(long incidentKey) {
    map.remove(incidentKey);
  }

  public IncidentMap wrapIncidentKey(long key) {
    final DirectBuffer result = map.get(key);

    if (result != null) {
      buffer.putBytes(0, result, 0, result.capacity());
    }
    this.isRead = result != null;
    this.key = key;

    return this;
  }

  public short getState() {
    return isRead ? buffer.getShort(STATE_OFFSET, BYTE_ORDER) : -1;
  }

  public long getIncidentEventPosition() {
    return isRead ? buffer.getLong(INCIDENT_EVENT_POSITION_OFFSET, BYTE_ORDER) : -1L;
  }

  public long getFailureEventPosition() {
    return isRead ? buffer.getLong(FAILURE_EVENT_POSITION_OFFSET, BYTE_ORDER) : -1L;
  }

  public IncidentMap newIncident(long incidentKey) {
    key = incidentKey;
    isRead = true;
    return this;
  }

  public void write() {
    ensureRead();
    map.put(key, buffer);
  }

  public IncidentMap setState(short state) {
    ensureRead();
    buffer.putShort(STATE_OFFSET, state, BYTE_ORDER);
    return this;
  }

  public IncidentMap setIncidentEventPosition(long position) {
    ensureRead();
    buffer.putLong(INCIDENT_EVENT_POSITION_OFFSET, position, BYTE_ORDER);
    return this;
  }

  public IncidentMap setFailureEventPosition(long position) {
    ensureRead();
    buffer.putLong(FAILURE_EVENT_POSITION_OFFSET, position, BYTE_ORDER);
    return this;
  }

  private void ensureRead() {
    if (!isRead) {
      throw new IllegalStateException("must call wrap() before");
    }
  }

  public void close() {
    map.close();
  }
}
