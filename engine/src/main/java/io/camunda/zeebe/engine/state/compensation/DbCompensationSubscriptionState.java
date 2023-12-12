/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.compensation;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.mutable.MutableCompensationSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;

public class DbCompensationSubscriptionState implements MutableCompensationSubscriptionState {

  private final DbLong processInstanceKey;
  private final DbString compensableActivityIdKey;
  private final DbString tenantIdKey;
  private final DbTenantAwareKey<DbLong> tenantAwareProcessInstanceKey;
  private final DbCompositeKey<DbTenantAwareKey<DbLong>, DbString>
      tenantAwareProcessInstanceKeyCompensableActivityId;
  private final ColumnFamily<
          DbCompositeKey<DbTenantAwareKey<DbLong>, DbString>, CompensationSubscription>
      compensationSubscriptionColumnFamily;
  private final CompensationSubscription compensationSubscription = new CompensationSubscription();

  public DbCompensationSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    processInstanceKey = new DbLong();
    compensableActivityIdKey = new DbString();
    tenantIdKey = new DbString();
    tenantAwareProcessInstanceKey =
        new DbTenantAwareKey<>(tenantIdKey, processInstanceKey, PlacementType.PREFIX);
    tenantAwareProcessInstanceKeyCompensableActivityId =
        new DbCompositeKey<>(tenantAwareProcessInstanceKey, compensableActivityIdKey);
    compensationSubscriptionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.COMPENSATION_SUBSCRIPTION,
            transactionContext,
            tenantAwareProcessInstanceKeyCompensableActivityId,
            compensationSubscription);
  }

  @Override
  public CompensationSubscription get(
      final String tenantId, final long processInstanceKey, final String compensableActivityId) {
    wrapCompensationKeys(processInstanceKey, compensableActivityId, tenantId);
    return compensationSubscriptionColumnFamily
        .get(tenantAwareProcessInstanceKeyCompensableActivityId)
        .copy();
  }

  @Override
  public void put(final long key, final CompensationSubscriptionRecord compensation) {
    compensationSubscription.setKey(key).setRecord(compensation);

    wrapCompensationKeys(
        compensation.getProcessInstanceKey(),
        compensation.getCompensableActivityId(),
        compensation.getTenantId());

    compensationSubscriptionColumnFamily.upsert(
        tenantAwareProcessInstanceKeyCompensableActivityId, compensationSubscription);
  }

  @Override
  public void update(final CompensationSubscriptionRecord compensation) {
    tenantIdKey.wrapString(compensation.getTenantId());
    processInstanceKey.wrapLong(compensation.getProcessInstanceKey());

    compensationSubscriptionColumnFamily.whileEqualPrefix(
        new DbCompositeKey<>(tenantIdKey, processInstanceKey),
        ((key, value) -> {
          value
              .getRecord()
              .setThrowEventId(compensation.getThrowEventId())
              .setThrowEventInstanceKey(compensation.getThrowEventInstanceKey());
          compensationSubscriptionColumnFamily.upsert(key, value);
        }));
  }

  private void wrapCompensationKeys(
      final long processInstance, final String compensableActivityId, final String tenantId) {
    processInstanceKey.wrapLong(processInstance);
    compensableActivityIdKey.wrapString(compensableActivityId);
    tenantIdKey.wrapString(tenantId);
  }
}
