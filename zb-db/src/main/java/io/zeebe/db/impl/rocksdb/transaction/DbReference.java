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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

class DbReference implements AutoCloseable {

  private final OptimisticTransactionDB transactionDB;
  private final AtomicLong referenceCount;
  private final Consumer<String> onCloseCallback;
  private final String databasePath;
  private final List<ColumnFamilyHandle> familyHandles;

  DbReference(
      String databasePath,
      OptimisticTransactionDB transactionDB,
      List<ColumnFamilyHandle> familyHandles,
      Consumer<String> onCloseCallback) {
    this.databasePath = databasePath;
    this.transactionDB = transactionDB;
    this.referenceCount = new AtomicLong(0);
    this.onCloseCallback = onCloseCallback;
    this.familyHandles = Collections.unmodifiableList(familyHandles);
  }

  OptimisticTransactionDB getTransactionDB() {
    return transactionDB;
  }

  DbReference newReference() {
    referenceCount.incrementAndGet();
    return this;
  }

  @Override
  public void close() {
    if (referenceCount.decrementAndGet() == 0) {
      onCloseCallback.accept(databasePath);
      transactionDB.close();
    }
  }

  Transaction beginTransaction(WriteOptions options) {
    return transactionDB.beginTransaction(options);
  }

  List<ColumnFamilyHandle> getFamilyHandles() {
    return familyHandles;
  }
}
