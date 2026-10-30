/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_4;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.migration.to_8_4.legacy.LegacySignalSubscriptionState;
import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class DbSignalSubscriptionMigrationState {

  private final LegacySignalSubscriptionState from;
  private final DbSignalSubscriptionState to;

  public DbSignalSubscriptionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    from = new LegacySignalSubscriptionState(zeebeDb, transactionContext);
    to = new DbSignalSubscriptionState(zeebeDb, transactionContext);
  }

  public void migrateSignalSubscriptionStateForMultiTenancy() {
    // setting the tenant id key once, because it's the same for all steps below
    to.tenantIdKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    /*
    `DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY` -> `SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY`
    - Prefix tenant to key
    */
    from.getSignalNameAndSubscriptionKeyColumnFamily()
        .forEach(
            (key, value) -> {
              final var subscriptionKey = key.second().getValue();
              final var signalName = key.first().getBuffer();

              to.signalName.wrapBuffer(signalName);
              to.subscriptionKey.wrapLong(subscriptionKey);

              to.signalNameAndSubscriptionKeyColumnFamily.insert(
                  to.tenantAwareSignalNameAndSubscriptionKey, value);

              from.getSignalNameAndSubscriptionKeyColumnFamily().deleteExisting(key);
            });

    from.getSubscriptionKeyAndSignalNameColumnFamily()
        .forEach(
            (key, dbNil) -> {
              final var subscriptionKey = key.first().getValue();
              final var signalName = key.second().getBuffer();

              to.subscriptionKey.wrapLong(subscriptionKey);
              to.signalName.wrapBuffer(signalName);

              to.subscriptionKeyAndSignalNameColumnFamily.insert(
                  to.tenantAwareSubscriptionKeyAndSignalName, DbNil.INSTANCE);

              from.getSubscriptionKeyAndSignalNameColumnFamily().deleteExisting(key);
            });
  }

  private static final class DbSignalSubscriptionState {
    private final DbString signalName;
    // processDefinitionKey or elementInstanceKey
    private final DbLong subscriptionKey;

    // [[tenant_id, signalName], subscriptionKey] => SignalSubscription
    private final DbString tenantIdKey;
    private final DbTenantAwareKey<DbString> tenantAwareSignalName;
    private final DbCompositeKey<DbTenantAwareKey<DbString>, DbLong>
        tenantAwareSignalNameAndSubscriptionKey;
    private final ColumnFamily<
            DbCompositeKey<DbTenantAwareKey<DbString>, DbLong>, SignalSubscription>
        signalNameAndSubscriptionKeyColumnFamily;
    private final SignalSubscription signalSubscription = new SignalSubscription();

    // [subscriptionKey, [tenantId, signalName]] => \0  : to find existing subscriptions of a
    // process
    // or element
    private final DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>
        tenantAwareSubscriptionKeyAndSignalName;
    private final ColumnFamily<DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>, DbNil>
        subscriptionKeyAndSignalNameColumnFamily;

    public DbSignalSubscriptionState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
      signalName = new DbString();
      subscriptionKey = new DbLong();
      tenantIdKey = new DbString();
      tenantAwareSignalName = new DbTenantAwareKey<>(tenantIdKey, signalName, PlacementType.PREFIX);
      tenantAwareSignalNameAndSubscriptionKey =
          new DbCompositeKey<>(tenantAwareSignalName, subscriptionKey);
      signalNameAndSubscriptionKeyColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
              transactionContext,
              tenantAwareSignalNameAndSubscriptionKey,
              signalSubscription);

      tenantAwareSubscriptionKeyAndSignalName =
          new DbCompositeKey<>(subscriptionKey, tenantAwareSignalName);
      subscriptionKeyAndSignalNameColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
              transactionContext,
              tenantAwareSubscriptionKeyAndSignalName,
              DbNil.INSTANCE);
    }
  }
}
