/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;

public final class DbMessageStartEventSubscriptionState
    implements MutableMessageStartEventSubscriptionState {

  private final DbString tenantId;
  private final DbString messageName;
  private final DbLong processDefinitionKey;

  // (tenantId, messageName, processDefinitionKey => MessageSubscription)
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>
      tenantAndMessageNameAndProcessDefinitionKey;
  private final DbCompositeKey<DbString, DbString> tenantAndMessageNameKey;
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>, MessageStartEventSubscription>
      subscriptionsColumnFamily;
  private final MessageStartEventSubscription messageStartEventSubscription =
      new MessageStartEventSubscription();

  // (processDefinitionKey, messageName) => \0  : to find existing subscriptions of a process
  private final DbCompositeKey<DbLong, DbString> processDefinitionKeyAndMessageName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil>
      subscriptionsOfProcessDefinitionKeyColumnFamily;

  public DbMessageStartEventSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    tenantId = new DbString();
    messageName = new DbString();
    processDefinitionKey = new DbLong();
    tenantAndMessageNameKey = new DbCompositeKey<>(tenantId, messageName);
    tenantAndMessageNameAndProcessDefinitionKey =
        new DbCompositeKey<>(tenantAndMessageNameKey, processDefinitionKey);
    subscriptionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
            transactionContext,
            tenantAndMessageNameAndProcessDefinitionKey,
            messageStartEventSubscription);

    processDefinitionKeyAndMessageName = new DbCompositeKey<>(processDefinitionKey, messageName);
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

    tenantId.wrapBuffer(subscription.getTenantIdBuffer());
    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());
    subscriptionsColumnFamily.upsert(
        tenantAndMessageNameAndProcessDefinitionKey, messageStartEventSubscription);
    subscriptionsOfProcessDefinitionKeyColumnFamily.upsert(
        processDefinitionKeyAndMessageName, DbNil.INSTANCE);
  }

  @Override
  public void remove(
      final long processDefinitionKey,
      final DirectBuffer messageName,
      final DirectBuffer tenantId) {
    this.tenantId.wrapBuffer(tenantId);
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageName.wrapBuffer(messageName);

    subscriptionsColumnFamily.deleteExisting(tenantAndMessageNameAndProcessDefinitionKey);
    subscriptionsOfProcessDefinitionKeyColumnFamily.deleteExisting(
        processDefinitionKeyAndMessageName);
  }

  @Override
  public boolean exists(final MessageStartEventSubscriptionRecord subscription) {
    tenantId.wrapBuffer(subscription.getTenantIdBuffer());
    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    return subscriptionsColumnFamily.exists(tenantAndMessageNameAndProcessDefinitionKey);
  }

  @Override
  public void visitSubscriptionsByMessageName(
      final DirectBuffer messageName,
      final DirectBuffer tenantId,
      final MessageStartEventSubscriptionVisitor visitor) {

    this.tenantId.wrapBuffer(tenantId);
    this.messageName.wrapBuffer(messageName);
    subscriptionsColumnFamily.whileEqualPrefix(
        tenantAndMessageNameKey,
        (key, value) -> {
          visitor.visit(value);
        });
  }

  @Override
  public void visitSubscriptionsByProcessDefinition(
      final long processDefinitionKey,
      final DirectBuffer tenantId,
      final MessageStartEventSubscriptionVisitor visitor) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.tenantId.wrapBuffer(tenantId);

    subscriptionsOfProcessDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        (key, value) -> {
          final var subscription =
              subscriptionsColumnFamily.get(tenantAndMessageNameAndProcessDefinitionKey);

          if (subscription != null) {
            visitor.visit(subscription);
          }
        });
  }
}
