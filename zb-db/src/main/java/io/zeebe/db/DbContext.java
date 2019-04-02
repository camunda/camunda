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
package io.zeebe.db;

import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;

/** Represents the shared state of a database interaction */
public interface DbContext {

  /**
   * Write the {@link DbKey} to the shared key buffer
   *
   * @param key the key to write
   */
  void writeKey(DbKey key);

  /** @return the shared key buffer array */
  byte[] getKeyBufferArray();

  /**
   * Write the {@link DbValue} to the shared value buffer
   *
   * @param value the value to write
   */
  void writeValue(DbValue value);

  /** @return the shared value buffer array */
  byte[] getValueBufferArray();

  /**
   * Wraps the buffer in the shared key view
   *
   * @param key the key buffer to wrap
   */
  void wrapKeyView(byte[] key);

  /** @return the shared key view */
  DirectBuffer getKeyView();

  /** @return true if the key view is currently empty, false otherwise */
  boolean isKeyViewEmpty();

  /**
   * Wraps the buffer in the shared value view
   *
   * @param value the value buffer to wrap
   */
  void wrapValueView(byte[] value);

  /** @return the shared value view */
  DirectBuffer getValueView();

  /** @return true if the value view is currently empty, false otherwise */
  boolean isValueViewEmpty();

  /**
   * Runs a consumer with a shared prefix key buffer
   *
   * @param prefixKeyBufferConsumer consumer of the shared prefix key buffer
   * @throws RuntimeException if no shared prefix buffer is available at the moment
   */
  void withPrefixKeyBuffer(Consumer<ExpandableArrayBuffer> prefixKeyBufferConsumer);

  /**
   * Create a new iterator on the shared transaction
   *
   * @param options the read options for the iterator
   * @param handle the column family handle for the iterator
   * @return the newly created iterator
   */
  RocksIterator newIterator(ReadOptions options, ColumnFamilyHandle handle);

  /**
   * Runs the commands like delete, put etc. in a transaction. Access of different column families
   * inside this transaction are possible.
   *
   * <p>Reading key-value pairs via get or an iterator is also possible and will reflect changes,
   * which are made during the transaction.
   *
   * <p><b>NOTE</b>: This will automatically commit the transaction and rollback on error
   *
   * @param operations the operations
   * @throws ZeebeDbException is thrown on an unexpected error in the database layer
   * @throws RuntimeException is thrown on an unexpected error in executing the operations
   */
  void runInTransaction(TransactionOperation operations);

  /**
   * This will return an transaction object, on which the caller can operate on. The caller is free
   * to decide when to commit or rollback the transaction.
   *
   * @return the transaction object
   */
  ZeebeDbTransaction getCurrentTransaction();
}
