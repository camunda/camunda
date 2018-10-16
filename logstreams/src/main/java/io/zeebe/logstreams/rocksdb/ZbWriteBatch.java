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
package io.zeebe.logstreams.rocksdb;

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;

import io.zeebe.util.EnsureUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.WriteBatch;

/**
 * Extension to {@link WriteBatch} to expose a few protected methods that allow us to reuse buffers
 * more easily.
 *
 * <p>NOTE: all methods that use {@link DirectBuffer} types explicitly read from the underlying byte
 * array starting at offset 0. This is a limitation of RocksDB which unfortunately doesn't provide
 * any native methods that use offset for those buffers yet.
 */
public class ZbWriteBatch extends WriteBatch {
  private static final Method PUT_METHOD;
  private static final Method DELETE_METHOD;

  static {
    try {
      PUT_METHOD =
          WriteBatch.class.getDeclaredMethod(
              "put", Long.TYPE, byte[].class, Integer.TYPE, byte[].class, Integer.TYPE, Long.TYPE);
      PUT_METHOD.setAccessible(true);

      DELETE_METHOD =
          WriteBatch.class.getDeclaredMethod(
              "delete", Long.TYPE, byte[].class, Integer.TYPE, Long.TYPE);
      DELETE_METHOD.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private final MutableDirectBuffer longKeyBuffer = new UnsafeBuffer(new byte[Long.BYTES]);

  public void put(
      ColumnFamilyHandle columnFamily, byte[] key, int keyLength, byte[] value, int valueLength) {
    try {
      PUT_METHOD.invoke(
          this,
          nativeHandle_,
          key,
          keyLength,
          value,
          valueLength,
          ZbRocksDb.getNativeHandle(columnFamily));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public void put(ColumnFamilyHandle columnFamily, DirectBuffer key, DirectBuffer value) {
    assertBuffers(key, value);
    put(columnFamily, key.byteArray(), key.capacity(), value.byteArray(), value.capacity());
  }

  public void put(ColumnFamilyHandle columnFamily, long key, byte[] value, int valueLength) {
    setKey(key);
    put(columnFamily, longKeyBuffer.byteArray(), longKeyBuffer.capacity(), value, valueLength);
  }

  public void delete(ColumnFamilyHandle columnFamily, final byte[] key, final int keyLength) {
    try {
      DELETE_METHOD.invoke(
          this, nativeHandle_, key, keyLength, ZbRocksDb.getNativeHandle(columnFamily));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public void delete(ColumnFamilyHandle columnFamily, DirectBuffer key) {
    assertBuffers(key);
    delete(columnFamily, key.byteArray(), key.capacity());
  }

  public void delete(ColumnFamilyHandle columnFamily, long key) {
    setKey(key);
    delete(columnFamily, longKeyBuffer);
  }

  private void assertBuffers(final DirectBuffer... buffers) {
    for (final DirectBuffer buffer : buffers) {
      EnsureUtil.ensureArrayBacked(buffer);
      assert buffer.wrapAdjustment() == 0 : "only supports reading from offset 0";
    }
  }

  private void setKey(final long key) {
    longKeyBuffer.putLong(0, key, STATE_BYTE_ORDER);
  }
}
