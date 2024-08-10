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
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.engine.state.migration.MemoryBoundedColumnIteration;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyMessageSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class DbMessageSubscriptionMigrationState {

  private final LegacyMessageSubscriptionState from;
  private final DbMessageSubscriptionState to;

  public DbMessageSubscriptionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    from = new LegacyMessageSubscriptionState(zeebeDb, transactionContext);
    to = new DbMessageSubscriptionState(zeebeDb, transactionContext);
  }

  public void migrateMessageSubscriptionForMultiTenancy() {
    final var iterator = new MemoryBoundedColumnIteration();
    // setting the tenant id key once, because it's the same for all steps below
    to.tenantIdKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    /*
    - `DEPRECATED_MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY` -> `MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY`
    - Prefix first part of composite key with tenant
     */
    iterator.drain(
        from.getMessageNameAndCorrelationKeyColumnFamily(),
        (key, value) -> {
          to.messageName.wrapBuffer(key.first().first().getBuffer());
          to.correlationKey.wrapBuffer(key.first().second().getBuffer());
          to.elementInstanceKey.wrapLong(key.second().getValue());
          to.messageNameAndCorrelationKeyColumnFamily.insert(
              to.tenantAwareNameCorrelationAndElementInstanceKey, DbNil.INSTANCE);
        });
  }

  private static final class DbMessageSubscriptionState {
    // (elementInstanceKey, messageName) => MessageSubscription
    private final DbLong elementInstanceKey;
    private final DbString messageName;
    private final MessageSubscription messageSubscription;
    private final DbCompositeKey<DbLong, DbString> elementKeyAndMessageName;
    private final ColumnFamily<DbCompositeKey<DbLong, DbString>, MessageSubscription>
        subscriptionColumnFamily;

    // (tenant aware messageName, correlationKey, elementInstanceKey) => \0
    private final DbString tenantIdKey;
    private final DbString correlationKey;
    private final DbTenantAwareKey<DbCompositeKey<DbString, DbString>>
        tenantAwareNameAndCorrelationKey;
    private final DbCompositeKey<DbTenantAwareKey<DbCompositeKey<DbString, DbString>>, DbLong>
        tenantAwareNameCorrelationAndElementInstanceKey;
    private final ColumnFamily<
            DbCompositeKey<DbTenantAwareKey<DbCompositeKey<DbString, DbString>>, DbLong>, DbNil>
        messageNameAndCorrelationKeyColumnFamily;

    public DbMessageSubscriptionState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

      elementInstanceKey = new DbLong();
      messageName = new DbString();
      messageSubscription = new MessageSubscription();
      elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, messageName);
      subscriptionColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY,
              transactionContext,
              elementKeyAndMessageName,
              messageSubscription);

      tenantIdKey = new DbString();
      correlationKey = new DbString();
      tenantAwareNameAndCorrelationKey =
          new DbTenantAwareKey<>(
              tenantIdKey, new DbCompositeKey<>(messageName, correlationKey), PlacementType.PREFIX);
      tenantAwareNameCorrelationAndElementInstanceKey =
          new DbCompositeKey<>(tenantAwareNameAndCorrelationKey, elementInstanceKey);
      messageNameAndCorrelationKeyColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,
              transactionContext,
              tenantAwareNameCorrelationAndElementInstanceKey,
              DbNil.INSTANCE);
    }
  }
}
