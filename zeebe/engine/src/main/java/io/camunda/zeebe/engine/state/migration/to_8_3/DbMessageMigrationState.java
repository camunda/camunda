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
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyMessageState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class DbMessageMigrationState {

  private final LegacyMessageState from;
  private final DbMessageState to;

  public DbMessageMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    // Hardcoded partition id as this is only relevant for metrics. It doesn't have any impact on
    // the migration.
    final int partitionId = -1;
    from = new LegacyMessageState(zeebeDb, transactionContext, partitionId);
    to = new DbMessageState(zeebeDb, transactionContext);
  }

  public void migrateMessageStateForMultiTenancy() {
    final var iterator = new MemoryBoundedColumnIteration();
    // setting the tenant id key once, because it's the same for all steps below
    to.tenantIdKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    /*
     `DEPRECATED_MESSAGES` -> `MESSAGES`
    - Prefix first part of nested composite key with tenant
     */
    iterator.drain(
        from.getNameCorrelationMessageColumnFamily(),
        (key, value) -> {
          to.messageName.wrapBuffer(key.first().first().getBuffer());
          to.correlationKey.wrapBuffer(key.first().second().getBuffer());
          to.messageKey.wrapLong(key.second().inner().getValue());
          to.nameCorrelationMessageColumnFamily.insert(
              to.nameCorrelationMessageKey, DbNil.INSTANCE);
        });
  }

  private static final class DbMessageState {

    private final DbLong messageKey;
    private final DbForeignKey<DbLong> fkMessage;

    /**
     * <pre>tenant aware message name | correlation key | key -> []
     *
     * find message by name and correlation key - the message key ensures the queue ordering
     */
    private final DbString tenantIdKey;

    private final DbString messageName;
    private final DbTenantAwareKey<DbString> tenantAwareMessageName;

    private final DbString correlationKey;
    private final DbCompositeKey<
            DbCompositeKey<DbTenantAwareKey<DbString>, DbString>, DbForeignKey<DbLong>>
        nameCorrelationMessageKey;
    private final DbCompositeKey<DbTenantAwareKey<DbString>, DbString> nameAndCorrelationKey;
    private final ColumnFamily<
            DbCompositeKey<
                DbCompositeKey<DbTenantAwareKey<DbString>, DbString>, DbForeignKey<DbLong>>,
            DbNil>
        nameCorrelationMessageColumnFamily;

    public DbMessageState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
      messageKey = new DbLong();
      fkMessage = new DbForeignKey<>(messageKey, ZbColumnFamilies.MESSAGE_KEY);

      tenantIdKey = new DbString();
      messageName = new DbString();
      tenantAwareMessageName =
          new DbTenantAwareKey<>(tenantIdKey, messageName, PlacementType.PREFIX);
      correlationKey = new DbString();
      nameAndCorrelationKey = new DbCompositeKey<>(tenantAwareMessageName, correlationKey);
      nameCorrelationMessageKey = new DbCompositeKey<>(nameAndCorrelationKey, fkMessage);
      nameCorrelationMessageColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.MESSAGES,
              transactionContext,
              nameCorrelationMessageKey,
              DbNil.INSTANCE);
    }
  }
}
