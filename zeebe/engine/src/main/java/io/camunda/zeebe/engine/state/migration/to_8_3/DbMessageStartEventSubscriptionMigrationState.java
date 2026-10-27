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
import io.camunda.zeebe.engine.state.message.MessageStartEventSubscription;
import io.camunda.zeebe.engine.state.migration.MemoryBoundedColumnIteration;
import io.camunda.zeebe.engine.state.migration.to_8_3.legacy.LegacyMessageStartEventSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class DbMessageStartEventSubscriptionMigrationState {

  private final LegacyMessageStartEventSubscriptionState from;
  private final DbMessageStartEventSubscriptionState to;

  public DbMessageStartEventSubscriptionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    from = new LegacyMessageStartEventSubscriptionState(zeebeDb, transactionContext);
    to = new DbMessageStartEventSubscriptionState(zeebeDb, transactionContext);
  }

  public void migrateMessageStartEventSubscriptionForMultiTenancy() {
    final var iterator = new MemoryBoundedColumnIteration();
    // setting the tenant id key once, because it's the same for all steps below
    to.tenantIdKey.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    /*
    - `DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY` -> `MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY`
    - Prefix first part of composite key with tenant
    - Set tenant on value
     */
    iterator.drain(
        from.getSubscriptionsColumnFamily(),
        (key, value) -> {
          value.getRecord().setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
          to.messageName.wrapBuffer(key.first().getBuffer());
          to.processDefinitionKey.wrapLong(key.second().getValue());
          to.subscriptionsColumnFamily.insert(to.messageNameAndProcessDefinitionKey, value);
        });

    /*
    - `DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME` -> `MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME`
    - Prefix second part of composite key with tenant
     */
    iterator.drain(
        from.getSubscriptionsOfProcessDefinitionKeyColumnFamily(),
        (key, value) -> {
          to.processDefinitionKey.wrapLong(key.first().getValue());
          to.messageName.wrapBuffer(key.second().getBuffer());
          to.subscriptionsOfProcessDefinitionKeyColumnFamily.insert(
              to.processDefinitionKeyAndMessageName, DbNil.INSTANCE);
        });
  }

  private static final class DbMessageStartEventSubscriptionState {

    private final DbString tenantIdKey;
    private final DbString messageName;
    private final DbTenantAwareKey<DbString> tenantAwareMessageName;
    private final DbLong processDefinitionKey;

    // (tenant aware messageName, processDefinitionKey => MessageSubscription)
    private final DbCompositeKey<DbTenantAwareKey<DbString>, DbLong>
        messageNameAndProcessDefinitionKey;
    private final ColumnFamily<
            DbCompositeKey<DbTenantAwareKey<DbString>, DbLong>, MessageStartEventSubscription>
        subscriptionsColumnFamily;
    private final MessageStartEventSubscription messageStartEventSubscription =
        new MessageStartEventSubscription();

    // (processDefinitionKey, tenant aware messageName) => \0  : to find existing subscriptions of a
    // process
    private final DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>
        processDefinitionKeyAndMessageName;
    private final ColumnFamily<DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>, DbNil>
        subscriptionsOfProcessDefinitionKeyColumnFamily;

    public DbMessageStartEventSubscriptionState(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
      tenantIdKey = new DbString();
      messageName = new DbString();
      tenantAwareMessageName =
          new DbTenantAwareKey<>(tenantIdKey, messageName, PlacementType.PREFIX);
      processDefinitionKey = new DbLong();
      messageNameAndProcessDefinitionKey =
          new DbCompositeKey<>(tenantAwareMessageName, processDefinitionKey);
      subscriptionsColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
              transactionContext,
              messageNameAndProcessDefinitionKey,
              messageStartEventSubscription);

      processDefinitionKeyAndMessageName =
          new DbCompositeKey<>(processDefinitionKey, tenantAwareMessageName);
      subscriptionsOfProcessDefinitionKeyColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
              transactionContext,
              processDefinitionKeyAndMessageName,
              DbNil.INSTANCE);
    }
  }
}
