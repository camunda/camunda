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
package io.zeebe.broker.workflow.state;

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;

import io.zeebe.logstreams.state.StateController;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;

public class NextValueManager {

  private final StateController rocksDbWrapper;
  private final MutableDirectBuffer nextValueBuffer;

  public NextValueManager(StateController rocksDbWrapper) {
    this.rocksDbWrapper = rocksDbWrapper;
    nextValueBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
  }

  public long getNextValue(ColumnFamilyHandle columnFamilyHandle, byte[] key) {
    final byte[] generateKeyBytes = nextValueBuffer.byteArray();
    final int readBytes =
        rocksDbWrapper.get(
            columnFamilyHandle, key, 0, key.length, generateKeyBytes, 0, generateKeyBytes.length);

    long previousKey = 0;
    final boolean keyWasFound = readBytes == generateKeyBytes.length;
    if (keyWasFound) {
      previousKey = nextValueBuffer.getLong(0, STATE_BYTE_ORDER);
    }

    final long nextKey = previousKey + 1;
    nextValueBuffer.putLong(0, nextKey, STATE_BYTE_ORDER);

    rocksDbWrapper.put(
        columnFamilyHandle, key, 0, key.length, generateKeyBytes, 0, generateKeyBytes.length);

    return nextKey;
  }
}
