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
package io.zeebe.db.impl.rocksdb.transaction;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;

class ZeebeTransaction {

  private final Transaction transaction;
  private final long nativeHandle;

  ZeebeTransaction(Transaction transaction) {
    this.transaction = transaction;
    try {
      nativeHandle = RocksDbInternal.nativeHandle.getLong(transaction);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  protected void put(
      long columnFamilyHandle, byte[] key, int keyLength, byte[] value, int valueLength)
      throws Exception {
    RocksDbInternal.putWithHandle.invoke(
        transaction, nativeHandle, key, keyLength, value, valueLength, columnFamilyHandle);
  }

  public byte[] get(long columnFamilyHandle, long readOptionsHandle, byte[] key, int keyLength)
      throws Exception {
    return (byte[])
        RocksDbInternal.getWithHandle.invoke(
            transaction, nativeHandle, readOptionsHandle, key, keyLength, columnFamilyHandle);
  }

  public void delete(long columnFamilyHandle, byte[] key, int keyLength) throws Exception {
    RocksDbInternal.removeWithHandle.invoke(
        transaction, nativeHandle, key, keyLength, columnFamilyHandle);
  }

  public RocksIterator newIterator(ReadOptions options, ColumnFamilyHandle handle) {
    return transaction.getIterator(options, handle);
  }

  public void commit() throws RocksDBException {
    transaction.commit();
  }

  public void close() {
    transaction.close();
  }
}
