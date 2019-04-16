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
import io.zeebe.db.DbContext;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
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

  private final DbContext context;

  private final ValueType valueInstance;
  private final KeyType keyInstance;

  TransactionalColumnFamily(
      ZeebeTransactionDb<ColumnFamilyNames> transactionDb,
      ColumnFamilyNames columnFamily,
      DbContext context,
      KeyType keyInstance,
      ValueType valueInstance) {
    this.transactionDb = transactionDb;
    handle = this.transactionDb.getColumnFamilyHandle(columnFamily);
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
  }

  @Override
  public void put(KeyType key, ValueType value) {
    put(context, key, value);
  }

  @Override
  public void put(DbContext context, KeyType key, ValueType value) {
    transactionDb.put(handle, context, key, value);
  }

  @Override
  public ValueType get(KeyType key) {
    return get(context, key);
  }

  public ValueType get(DbContext context, KeyType key) {
    return get(context, key, valueInstance);
  }

  @Override
  public ValueType get(DbContext context, KeyType key, ValueType value) {
    final DirectBuffer valueBuffer = transactionDb.get(handle, context, key);
    if (valueBuffer != null) {

      value.wrap(valueBuffer, 0, valueBuffer.capacity());
      return value;
    }
    return null;
  }

  @Override
  public void forEach(Consumer<ValueType> consumer) {
    forEach(context, consumer);
  }

  public void forEach(DbContext context, Consumer<ValueType> consumer) {
    transactionDb.foreach(handle, context, valueInstance, consumer);
  }

  @Override
  public void forEach(BiConsumer<KeyType, ValueType> consumer) {
    forEach(context, consumer);
  }

  public void forEach(DbContext context, BiConsumer<KeyType, ValueType> consumer) {
    transactionDb.foreach(handle, context, keyInstance, valueInstance, consumer);
  }

  @Override
  public void whileTrue(KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileTrue(context, visitor);
  }

  public void whileTrue(DbContext context, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileTrue(context, visitor, keyInstance, valueInstance);
  }

  @Override
  public void whileTrue(
      DbContext context,
      KeyValuePairVisitor<KeyType, ValueType> visitor,
      KeyType key,
      ValueType value) {
    transactionDb.whileTrue(handle, context, key, value, visitor);
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, visitor);
  }

  public void whileEqualPrefix(
      DbContext context, DbKey keyPrefix, BiConsumer<KeyType, ValueType> visitor) {
    transactionDb.whileEqualPrefix(handle, context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, visitor);
  }

  public void whileEqualPrefix(
      DbContext context, DbKey keyPrefix, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    transactionDb.whileEqualPrefix(handle, context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  @Override
  public void delete(KeyType key) {
    delete(context, key);
  }

  @Override
  public void delete(DbContext context, KeyType key) {
    transactionDb.delete(handle, context, key);
  }

  @Override
  public boolean exists(KeyType key) {
    return exists(context, key);
  }

  public boolean exists(DbContext context, KeyType key) {
    return transactionDb.exists(handle, context, key);
  }

  @Override
  public boolean isEmpty() {
    return isEmpty(context);
  }

  @Override
  public boolean isEmpty(DbContext context) {
    return transactionDb.isEmpty(handle, context);
  }
}
