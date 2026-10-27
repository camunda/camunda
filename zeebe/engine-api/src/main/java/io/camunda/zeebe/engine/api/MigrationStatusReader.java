/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;

/** Read-only access to the migration status stored in a partition's state. */
public final class MigrationStatusReader {

  private final DbMigrationState state;

  MigrationStatusReader(final ZeebeDb<ZbColumnFamilies> zeebeDb) {
    state = new DbMigrationState(zeebeDb, zeebeDb.createContext());
  }

  /** Returns the version that last migrated the state, or {@code null} if none is recorded. */
  public String getMigratedByVersion() {
    return state.getMigratedByVersion();
  }
}
