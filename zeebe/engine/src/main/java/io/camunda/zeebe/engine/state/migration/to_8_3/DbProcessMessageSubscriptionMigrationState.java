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
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.migration.MemoryBoundedColumnIteration;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class DbProcessMessageSubscriptionMigrationState {

  private final LegacyProcessMessageSubscriptionState from;
  private final DbProcessMessageSubscriptionState to;

  public DbProcessMessageSubscriptionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    from = new LegacyProcessMessageSubscriptionState(zeebeDb, transactionContext);
    to = new DbProcessMessageSubscriptionState(zeebeDb, transactionContext);
  }

  public void migrateProcessMessageSubscriptionForMultiTenancy() {
    final var iterator = new MemoryBoundedColumnIteration();
    // setting the tenant id key once, because it's the same for all steps below
    to.tenantIdKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    /*
    - `DEPRECATED_PROCESS_SUBSCRIPTION_BY_KEY` -> `PROCESS_SUBSCRIPTION_BY_KEY`
    - Prefix second part of composite key with tenant
    - Set tenant on value
     */
    iterator.drain(
        from.getSubscriptionColumnFamily(),
        (key, value) -> {
          to.elementInstanceKey.wrapLong(key.first().getValue());
          to.messageName.wrapBuffer(key.second().getBuffer());
          value.getRecord().setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
          to.subscriptionColumnFamily.insert(to.elementKeyAndMessageName, value);
        });
  }

  private static final class DbProcessMessageSubscriptionState {
    // (elementInstanceKey, tenant aware messageName) => ProcessMessageSubscription
    private final DbLong elementInstanceKey;

    private final DbString tenantIdKey;
    private final DbString messageName;
    private final DbTenantAwareKey<DbString> tenantAwareMessageName;
    private final DbCompositeKey<DbLong, DbTenantAwareKey<DbString>> elementKeyAndMessageName;
    private final ProcessMessageSubscription processMessageSubscription;
    private final ColumnFamily<
            DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>, ProcessMessageSubscription>
        subscriptionColumnFamily;

    public DbProcessMessageSubscriptionState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
      elementInstanceKey = new DbLong();
      tenantIdKey = new DbString();
      messageName = new DbString();
      tenantAwareMessageName =
          new DbTenantAwareKey<>(tenantIdKey, messageName, PlacementType.PREFIX);
      elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, tenantAwareMessageName);
      processMessageSubscription = new ProcessMessageSubscription();

      subscriptionColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_KEY,
              transactionContext,
              elementKeyAndMessageName,
              processMessageSubscription);
    }
  }
}
