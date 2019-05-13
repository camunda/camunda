/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.util;

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
