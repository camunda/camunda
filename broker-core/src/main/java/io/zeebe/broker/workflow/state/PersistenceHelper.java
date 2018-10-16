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

import io.zeebe.broker.Loggers;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.util.buffer.BufferReader;
import org.agrona.DirectBuffer;
import org.rocksdb.ColumnFamilyHandle;

public class PersistenceHelper {

  public static final byte[] EXISTENCE = new byte[] {1};

  private final StateController rocksDbWrapper;

  public PersistenceHelper(StateController rocksDbWrapper) {
    this.rocksDbWrapper = rocksDbWrapper;
  }

  public <T extends BufferReader> T getValueInstance(
      Class<T> clazz,
      ColumnFamilyHandle handle,
      DirectBuffer keyBuffer,
      int keyOffset,
      int keyLength,
      DirectBuffer valueBuffer) {
    final int valueLength = valueBuffer.capacity();
    final int readBytes =
        rocksDbWrapper.get(
            handle,
            keyBuffer.byteArray(),
            keyOffset,
            keyLength,
            valueBuffer.byteArray(),
            0,
            valueLength);

    if (readBytes >= valueLength) {
      valueBuffer.checkLimit(readBytes);
      return getValueInstance(clazz, handle, keyBuffer, keyOffset, keyLength, valueBuffer);
    } else if (readBytes <= 0) {
      return null;
    } else {
      try {
        final T instance = clazz.newInstance();
        instance.wrap(valueBuffer, 0, readBytes);
        return instance;
      } catch (Exception ex) {
        Loggers.STREAM_PROCESSING.error("Error in instantiating class " + clazz.getName(), ex);
        return null;
      }
    }
  }
}
