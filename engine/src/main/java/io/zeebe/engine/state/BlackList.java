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
import io.zeebe.db.impl.DbNil;

public class BlackList {

  private final ColumnFamily<DbLong, DbNil> blackListColumnFamily;
  private final DbLong workflowInstanceKey;

  public BlackList(ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    workflowInstanceKey = new DbLong();
    blackListColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BLACKLIST, dbContext, workflowInstanceKey, DbNil.INSTANCE);
  }

  public void blacklist(long key) {
    workflowInstanceKey.wrapLong(key);
    blackListColumnFamily.put(workflowInstanceKey, DbNil.INSTANCE);
  }

  public boolean isOnBlacklist(long key) {
    workflowInstanceKey.wrapLong(key);
    return blackListColumnFamily.exists(workflowInstanceKey);
  }
}
