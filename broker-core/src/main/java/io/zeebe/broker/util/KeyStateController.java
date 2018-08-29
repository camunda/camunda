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

import static io.zeebe.util.StringUtil.getBytes;

import io.zeebe.logstreams.state.StateController;
import java.io.File;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.RocksDB;

public class KeyStateController extends StateController {
  public static final byte[] LATEST_KEY_BUFFER = getBytes("latestKey");
  private final MutableDirectBuffer dbLongBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
  private AtomicReference<Runnable> onOpenCallback = new AtomicReference<>();

  public KeyStateController() {
    onOpenCallback.set(() -> {});
  }

  public void addOnOpenCallback(Runnable runnable) {
    Objects.requireNonNull(runnable);

    final Runnable existingCallback = onOpenCallback.get();
    onOpenCallback.compareAndSet(existingCallback, runnable);
  }

  @Override
  public RocksDB open(File dbDirectory, boolean reopen) throws Exception {
    final RocksDB rocksDB = super.open(dbDirectory, reopen);
    if (isOpened()) {
      onOpenCallback.get().run();
    }
    return rocksDB;
  }

  public long getLatestKey() {
    ensureIsOpened("recoverLatestKey");

    long latestKey = Long.MIN_VALUE;
    if (tryGet(LATEST_KEY_BUFFER, dbLongBuffer.byteArray())) {
      latestKey = dbLongBuffer.getLong(0, ByteOrder.LITTLE_ENDIAN);
    }
    return latestKey;
  }

  public void putLatestKey(long key) {
    ensureIsOpened("putLatestKey");

    dbLongBuffer.putLong(0, key, ByteOrder.LITTLE_ENDIAN);
    put(LATEST_KEY_BUFFER, dbLongBuffer.byteArray());
  }
}
