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

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
import io.zeebe.db.impl.rocksdb.DbContext;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

class TransactionalColumnFamily<
        ColumnFamilyNames extends Enum<ColumnFamilyNames>,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final ZeebeTransactionDb<ColumnFamilyNames> transactionDb;
  private final long handle;

  private final ValueType valueInstance;
  private final KeyType keyInstance;
  private final DbContext dbContext;

  TransactionalColumnFamily(
      final DbContext dbContext,
      ZeebeTransactionDb<ColumnFamilyNames> transactionDb,
      ColumnFamilyNames columnFamily,
      KeyType keyInstance,
      ValueType valueInstance) {
    this.dbContext = dbContext;
    this.transactionDb = transactionDb;
    this.handle = transactionDb.getColumnFamilyHandle(columnFamily);
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
  }

  @Override
  public void put(KeyType key, ValueType value) {
    put(dbContext, key, value);
  }

  @Override
  public void put(DbContext dbContext, KeyType key, ValueType value) {
    dbContext.runInTransaction(() -> transactionDb.put(dbContext, handle, key, value));
  }

  @Override
  public ValueType get(KeyType key) {
    return get(dbContext, key);
  }

  @Override
  public ValueType get(final DbContext dbContext, final KeyType key) {
    final AtomicBoolean foundValue = new AtomicBoolean();

    dbContext.runInTransaction(
        () -> {
          final DirectBuffer valueBuffer = transactionDb.get(dbContext, handle, key);
          if (valueBuffer != null) {
            valueInstance.wrap(valueBuffer, 0, valueBuffer.capacity());
            foundValue.set(true);
          } else {
            foundValue.set(false);
          }
        });

    return foundValue.get() ? valueInstance : null;
  }

  @Override
  public void forEach(Consumer<ValueType> consumer) {
    forEach(dbContext, consumer);
  }

  @Override
  public void forEach(final DbContext dbContext, final Consumer<ValueType> consumer) {
    dbContext.runInTransaction(
        () -> transactionDb.foreach(dbContext, handle, valueInstance, consumer));
  }

  @Override
  public void forEach(BiConsumer<KeyType, ValueType> consumer) {
    forEach(dbContext, consumer);
  }

  @Override
  public void forEach(final DbContext dbContext, final BiConsumer<KeyType, ValueType> consumer) {
    dbContext.runInTransaction(
        () -> transactionDb.foreach(dbContext, handle, keyInstance, valueInstance, consumer));
  }

  @Override
  public void whileTrue(KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileTrue(dbContext, visitor);
  }

  @Override
  public void whileTrue(
      final DbContext dbContext, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    dbContext.runInTransaction(
        () -> transactionDb.whileTrue(dbContext, handle, keyInstance, valueInstance, visitor));
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(dbContext, keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbContext dbContext,
      final DbKey keyPrefix,
      final BiConsumer<KeyType, ValueType> visitor) {
    dbContext.runInTransaction(
        () ->
            transactionDb.whileEqualPrefix(
                dbContext, handle, keyPrefix, keyInstance, valueInstance, visitor));
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(dbContext, keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbContext dbContext,
      final DbKey keyPrefix,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    dbContext.runInTransaction(
        () ->
            transactionDb.whileEqualPrefix(
                dbContext, handle, keyPrefix, keyInstance, valueInstance, visitor));
  }

  @Override
  public void delete(KeyType key) {
    delete(dbContext, key);
  }

  @Override
  public void delete(final DbContext dbContext, final KeyType key) {
    dbContext.runInTransaction(() -> transactionDb.delete(dbContext, handle, key));
  }

  @Override
  public boolean exists(KeyType key) {
    return exists(dbContext, key);
  }

  @Override
  public boolean exists(final DbContext dbContext, final KeyType key) {
    final AtomicBoolean exists = new AtomicBoolean();
    dbContext.runInTransaction(() -> exists.set(transactionDb.exists(dbContext, handle, key)));
    return exists.get();
  }

  @Override
  public boolean isEmpty() {
    return isEmpty(dbContext);
  }

  @Override
  public boolean isEmpty(final DbContext dbContext) {
    final AtomicBoolean isEmpty = new AtomicBoolean();
    dbContext.runInTransaction(() -> isEmpty.set(transactionDb.isEmpty(dbContext, handle)));
    return isEmpty.get();
  }
}
