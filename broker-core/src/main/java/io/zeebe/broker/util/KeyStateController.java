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
package io.zeebe.broker.util;

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;
import static io.zeebe.util.StringUtil.getBytes;

import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import io.zeebe.logstreams.state.StateController;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.rocksdb.ColumnFamilyHandle;

public class KeyStateController extends StateController {
  private static final byte[] KEY_HANDLE_NAME = getBytes("keyColumn");
  private static final byte[] NEXT_KEY_BUFFER = getBytes("nextKey");

  private final AtomicReference<Runnable> onOpenCallback = new AtomicReference<>();
  private ColumnFamilyHandle keyHandle;

  public KeyStateController() {
    onOpenCallback.set(() -> {});
  }

  public void addOnOpenCallback(Runnable runnable) {
    Objects.requireNonNull(runnable);

    final Runnable existingCallback = onOpenCallback.get();
    onOpenCallback.compareAndSet(existingCallback, runnable);
  }

  @Override
  public ZbRocksDb open(File dbDirectory, boolean reopen) throws Exception {
    final ZbRocksDb rocksDB = super.open(dbDirectory, reopen, Arrays.asList(KEY_HANDLE_NAME));

    keyHandle = getColumnFamilyHandle(KEY_HANDLE_NAME);

    if (isOpened()) {
      onOpenCallback.get().run();
    }
    return rocksDB;
  }

  @Override
  public ZbRocksDb open(File dbDirectory, boolean reopen, List<byte[]> columnFamilyNames)
      throws Exception {
    columnFamilyNames.add(KEY_HANDLE_NAME);
    final ZbRocksDb rocksDB = super.open(dbDirectory, reopen, columnFamilyNames);

    keyHandle = getColumnFamilyHandle(KEY_HANDLE_NAME);

    if (isOpened()) {
      onOpenCallback.get().run();
    }
    return rocksDB;
  }

  public long getNextKey() {
    ensureIsOpened("getNextKey");

    long latestKey = Long.MIN_VALUE;
    final int readBytes =
        get(
            keyHandle,
            NEXT_KEY_BUFFER,
            0,
            NEXT_KEY_BUFFER.length,
            dbLongBuffer.byteArray(),
            0,
            dbLongBuffer.capacity());

    if (readBytes > dbLongBuffer.capacity()) {
      throw new IllegalStateException("Key value is larger then it should be.");
    } else if (readBytes > 0) {
      latestKey = dbLongBuffer.getLong(0, STATE_BYTE_ORDER);
    }
    return latestKey;
  }

  public void putNextKey(long key) {
    ensureIsOpened("putNextKey");

    dbLongBuffer.putLong(0, key, STATE_BYTE_ORDER);
    put(
        keyHandle,
        NEXT_KEY_BUFFER,
        0,
        NEXT_KEY_BUFFER.length,
        dbLongBuffer.byteArray(),
        0,
        dbLongBuffer.capacity());
  }
}
