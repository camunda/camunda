/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;

public final class DbMessageStartEventSubscriptionState
    implements MutableMessageStartEventSubscriptionState {

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
    tenantAwareMessageName = new DbTenantAwareKey<>(tenantIdKey, messageName, PlacementType.PREFIX);
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

  @Override
  public void put(final long key, final MessageStartEventSubscriptionRecord subscription) {
    messageStartEventSubscription.setKey(key).setRecord(subscription);

    tenantIdKey.wrapString(subscription.getTenantId());
    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());
    subscriptionsColumnFamily.upsert(
        messageNameAndProcessDefinitionKey, messageStartEventSubscription);
    subscriptionsOfProcessDefinitionKeyColumnFamily.upsert(
        processDefinitionKeyAndMessageName, DbNil.INSTANCE);
  }

  @Override
  public void remove(
      final long processDefinitionKey, final DirectBuffer messageName, final String tenantId) {
    tenantIdKey.wrapString(tenantId);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageName.wrapBuffer(messageName);

    subscriptionsColumnFamily.deleteExisting(messageNameAndProcessDefinitionKey);
    subscriptionsOfProcessDefinitionKeyColumnFamily.deleteExisting(
        processDefinitionKeyAndMessageName);
  }

  @Override
  public boolean exists(final MessageStartEventSubscriptionRecord subscription) {
    tenantIdKey.wrapString(subscription.getTenantId());
    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    return subscriptionsColumnFamily.exists(messageNameAndProcessDefinitionKey);
  }

  @Override
  public void visitSubscriptionsByMessageName(
      final String tenantId,
      final DirectBuffer messageName,
      final MessageStartEventSubscriptionVisitor visitor) {

    tenantIdKey.wrapString(tenantId);
    this.messageName.wrapBuffer(messageName);
    subscriptionsColumnFamily.whileEqualPrefix(
        tenantAwareMessageName,
        (key, value) -> {
          visitor.visit(value);
        });
  }

  @Override
  public void visitSubscriptionsByProcessDefinition(
      final long processDefinitionKey, final MessageStartEventSubscriptionVisitor visitor) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    subscriptionsOfProcessDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        (key, value) -> {
          tenantIdKey.wrapBuffer(key.second().tenantKey().getBuffer());
          final var subscription =
              subscriptionsColumnFamily.get(messageNameAndProcessDefinitionKey);

          if (subscription != null) {
            visitor.visit(subscription);
          }
        });
  }
}
