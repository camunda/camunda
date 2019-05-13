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
package io.zeebe.engine.state;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;

public class NextValueManager {

  private static final int INITIAL_VALUE = 0;

  private final long initialValue;

  private final ColumnFamily<DbString, DbLong> nextValueColumnFamily;
  private final DbString nextValueKey;
  private final DbLong nextValue;

  public NextValueManager(
      ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext, ZbColumnFamilies columnFamily) {
    this(INITIAL_VALUE, zeebeDb, dbContext, columnFamily);
  }

  public NextValueManager(
      long initialValue,
      ZeebeDb<ZbColumnFamilies> zeebeDb,
      DbContext dbContext,
      ZbColumnFamilies columnFamily) {
    this.initialValue = initialValue;

    nextValueKey = new DbString();
    nextValue = new DbLong();
    nextValueColumnFamily =
        zeebeDb.createColumnFamily(columnFamily, dbContext, nextValueKey, nextValue);
  }

  public long getNextValue(String key) {
    nextValueKey.wrapString(key);

    final DbLong zbLong = nextValueColumnFamily.get(nextValueKey);

    long previousKey = initialValue;
    if (zbLong != null) {
      previousKey = zbLong.getValue();
    }

    final long nextKey = previousKey + 1;
    nextValue.wrapLong(nextKey);
    nextValueColumnFamily.put(nextValueKey, nextValue);

    return nextKey;
  }
}
