/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
