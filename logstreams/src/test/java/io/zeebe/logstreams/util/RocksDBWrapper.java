/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.util;

import static io.zeebe.util.StringUtil.getBytes;

import java.nio.ByteBuffer;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

// Slow and inefficient, only for testing
public class RocksDBWrapper {
  private RocksDB db;

  public void wrap(RocksDB db) {
    this.db = db;
  }

  public int getInt(String key) throws RocksDBException {
    return toInt(db.get(getBytes(key)));
  }

  public void putInt(String key, int value) throws RocksDBException {
    db.put(getBytes(key), ofInt(value));
  }

  public long getLong(String key) throws RocksDBException {
    return toLong(db.get(getBytes(key)));
  }

  public void putLong(String key, long value) throws RocksDBException {
    db.put(getBytes(key), ofLong(value));
  }

  public boolean mayExist(String key) throws RocksDBException {
    final StringBuilder builder = new StringBuilder();
    return db.keyMayExist(getBytes(key), builder);
  }

  // CONVERSION

  private byte[] ofInt(int value) {
    final ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
    buffer.putInt(value);

    return buffer.array();
  }

  private int toInt(byte[] value) {
    return ByteBuffer.wrap(value).getInt();
  }

  private byte[] ofLong(long value) {
    final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(value);

    return buffer.array();
  }

  private long toLong(byte[] value) {
    return ByteBuffer.wrap(value).getLong();
  }
}
