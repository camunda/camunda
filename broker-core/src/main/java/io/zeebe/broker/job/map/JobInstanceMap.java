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
package io.zeebe.broker.job.map;

import static org.agrona.BitUtil.SIZE_OF_SHORT;

import io.zeebe.map.Long2BytesZbMap;
import java.nio.ByteOrder;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Maps <b>job instance key</b> to
 * <li>state
 * <li>worker length
 * <li>worker (max 64 chars)
 */
public class JobInstanceMap {
  private static final int MAP_VALUE_SIZE = SIZE_OF_SHORT;
  private static final int STATE_OFFSET = 0;

  private static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[MAP_VALUE_SIZE]);

  private final Long2BytesZbMap map;

  public JobInstanceMap() {
    this.map = new Long2BytesZbMap(MAP_VALUE_SIZE);
  }

  public void remove(long workflowInstanceKey) {
    map.remove(workflowInstanceKey);
  }

  public Long2BytesZbMap getMap() {
    return map;
  }

  public short getJobState(long key) {
    final DirectBuffer result = map.get(key);

    if (result != null) {
      return result.getShort(STATE_OFFSET, BYTE_ORDER);
    } else {
      return -1;
    }
  }

  public void putJobInstance(long jobInstanceKey, short state) {
    buffer.putShort(STATE_OFFSET, state, BYTE_ORDER);
    map.put(jobInstanceKey, buffer);
  }

  public void close() {
    map.close();
  }
}
