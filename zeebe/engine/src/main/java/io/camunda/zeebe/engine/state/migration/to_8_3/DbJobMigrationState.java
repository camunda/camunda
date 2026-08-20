/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.migration.MemoryBoundedColumnIteration;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyJobState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class DbJobMigrationState {

  private final LegacyJobState from;
  private final DbJobState to;

  public DbJobMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    from = new LegacyJobState(zeebeDb, transactionContext);
    to = new DbJobState(zeebeDb, transactionContext);
  }

  public void migrateJobStateForMultiTenancy() {
    final var iterator = new MemoryBoundedColumnIteration();
    // setting the tenant id key once, because it's the same for all steps below
    to.tenantIdKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    /*
    `DEPRECATED_JOB_ACTIVATABLE` -> `JOB_ACTIVATABLE`
    - Suffix tenant to key
    */
    iterator.drain(
        from.getActivatableColumnFamily(),
        (key, value) -> {
          to.jobTypeKey.wrapString(key.first().toString());
          to.fkJob.inner().wrapLong(key.second().inner().getValue());
          to.activatableColumnFamily.insert(to.tenantAwareTypeJobKey, DbNil.INSTANCE);
        });
  }

  private static final class DbJobState {

    private final DbLong jobKey;
    private final DbForeignKey<DbLong> fkJob;

    // [[type, key], tenant_id] => nil
    private final DbString jobTypeKey;
    private final DbString tenantIdKey;
    private final DbCompositeKey<DbString, DbForeignKey<DbLong>> typeJobKey;
    private final DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>
        tenantAwareTypeJobKey;
    private final ColumnFamily<
            DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>, DbNil>
        activatableColumnFamily;

    public DbJobState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

      jobKey = new DbLong();
      fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);

      jobTypeKey = new DbString();
      tenantIdKey = new DbString();
      typeJobKey = new DbCompositeKey<>(jobTypeKey, fkJob);
      tenantAwareTypeJobKey = new DbTenantAwareKey<>(tenantIdKey, typeJobKey, PlacementType.SUFFIX);
      activatableColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.JOB_ACTIVATABLE,
              transactionContext,
              tenantAwareTypeJobKey,
              DbNil.INSTANCE);
    }
  }
}
