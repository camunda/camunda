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
package io.zeebe.db.impl.rocksdb;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

class RocksDbColumnFamily<
        ColumnFamilyNames extends Enum<ColumnFamilyNames>,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final ZeebeRocksDb<ColumnFamilyNames> zeebeRocksDb;
  private final long handle;

  private final ValueType valueInstance;
  private final KeyType keyInstance;

  RocksDbColumnFamily(
      ZeebeRocksDb<ColumnFamilyNames> zeebeRocksDb,
      ColumnFamilyNames columnFamily,
      KeyType keyInstance,
      ValueType valueInstance) {
    this.zeebeRocksDb = zeebeRocksDb;
    handle = zeebeRocksDb.getColumnFamilyHandle(columnFamily);
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
  }

  @Override
  public void put(KeyType key, ValueType value) {
    zeebeRocksDb.put(handle, key, value);
  }

  @Override
  public ValueType get(KeyType key) {
    final DirectBuffer valueBuffer = zeebeRocksDb.get(handle, key);
    if (valueBuffer != null) {
      valueInstance.wrap(valueBuffer, 0, valueBuffer.capacity());
      return valueInstance;
    }
    return null;
  }

  @Override
  public void forEach(Consumer<ValueType> consumer) {
    zeebeRocksDb.foreach(handle, valueInstance, consumer);
  }

  @Override
  public void forEach(BiConsumer<KeyType, ValueType> consumer) {
    zeebeRocksDb.foreach(handle, keyInstance, valueInstance, consumer);
  }

  @Override
  public void whileTrue(KeyValuePairVisitor<KeyType, ValueType> visitor) {
    zeebeRocksDb.whileTrue(handle, keyInstance, valueInstance, visitor);
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, BiConsumer<KeyType, ValueType> visitor) {
    zeebeRocksDb.whileEqualPrefix(handle, keyPrefix, keyInstance, valueInstance, visitor);
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    zeebeRocksDb.whileEqualPrefix(handle, keyPrefix, keyInstance, valueInstance, visitor);
  }

  @Override
  public void delete(KeyType key) {
    zeebeRocksDb.delete(handle, key);
  }

  @Override
  public boolean exists(KeyType key) {
    return zeebeRocksDb.exists(handle, key);
  }

  @Override
  public boolean isEmpty() {
    return zeebeRocksDb.isEmpty(handle);
  }
}
