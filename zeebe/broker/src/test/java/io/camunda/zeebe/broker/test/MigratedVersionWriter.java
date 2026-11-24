/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.test;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/** Records in the state the version that last migrated it, as the engine's migrations do. */
public final class MigratedVersionWriter {

  private MigratedVersionWriter() {}

  public static void writeMigratedByVersion(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final String version) {
    new DbMigrationState(zeebeDb, zeebeDb.createContext()).setMigratedByVersion(version);
  }
}
