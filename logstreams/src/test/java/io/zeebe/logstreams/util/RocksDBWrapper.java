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

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import io.zeebe.db.impl.DefaultColumnFamily;

public class RocksDBWrapper {

  private DbString key;
  private DbLong value;
  private ColumnFamily<DbString, DbLong> defaultColumnFamily;

  public void wrap(ZeebeDb<DefaultColumnFamily> db) {
    key = new DbString();
    value = new DbLong();
    defaultColumnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
  }

  public int getInt(String key) {
    this.key.wrapString(key);
    final DbLong zbLong = defaultColumnFamily.get(this.key);
    return zbLong != null ? (int) zbLong.getValue() : -1;
  }

  public void putInt(String key, int value) {
    this.key.wrapString(key);
    this.value.wrapLong(value);
    defaultColumnFamily.put(this.key, this.value);
  }

  public boolean mayExist(String key) {
    this.key.wrapString(key);
    return defaultColumnFamily.exists(this.key);
  }
}
