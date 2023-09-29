/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3.legacy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import org.agrona.DirectBuffer;

public final class LegacyProcessMessageSubscriptionState {

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

  public LegacyProcessMessageSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    elementInstanceKey = new DbLong();
    tenantIdKey = new DbString();
    messageName = new DbString();
    tenantAwareMessageName = new DbTenantAwareKey<>(tenantIdKey, messageName, PlacementType.PREFIX);
    elementKeyAndMessageName = new DbCompositeKey<>(elementInstanceKey, tenantAwareMessageName);
    processMessageSubscription = new ProcessMessageSubscription();

    subscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_PROCESS_SUBSCRIPTION_BY_KEY,
            transactionContext,
            elementKeyAndMessageName,
            processMessageSubscription);
  }

  public void put(final long key, final ProcessMessageSubscriptionRecord record) {
    wrapSubscriptionKeys(
        record.getElementInstanceKey(), record.getMessageNameBuffer(), record.getTenantId());

    processMessageSubscription.reset();
    processMessageSubscription.setKey(key).setRecord(record);

    subscriptionColumnFamily.insert(elementKeyAndMessageName, processMessageSubscription);
  }

  private void wrapSubscriptionKeys(
      final long elementInstanceKey, final DirectBuffer messageName, final String tenantId) {
    this.elementInstanceKey.wrapLong(elementInstanceKey);
    this.messageName.wrapBuffer(messageName);
    tenantIdKey.wrapString(tenantId);
  }

  public ColumnFamily<
          DbCompositeKey<DbLong, DbTenantAwareKey<DbString>>, ProcessMessageSubscription>
      getSubscriptionColumnFamily() {
    return subscriptionColumnFamily;
  }
}
