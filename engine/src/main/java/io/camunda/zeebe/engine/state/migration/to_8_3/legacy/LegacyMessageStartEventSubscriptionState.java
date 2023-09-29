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
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState.MessageStartEventSubscriptionVisitor;
import io.camunda.zeebe.engine.state.message.MessageStartEventSubscription;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;

public final class LegacyMessageStartEventSubscriptionState {

  private final DbString messageName;
  private final DbLong processDefinitionKey;

  // (messageName, processDefinitionKey => MessageSubscription)
  private final DbCompositeKey<DbString, DbLong> messageNameAndProcessDefinitionKey;
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, MessageStartEventSubscription>
      subscriptionsColumnFamily;
  private final MessageStartEventSubscription messageStartEventSubscription =
      new MessageStartEventSubscription();

  // (processDefinitionKey, messageName) => \0  : to find existing subscriptions of a process
  private final DbCompositeKey<DbLong, DbString> processDefinitionKeyAndMessageName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil>
      subscriptionsOfProcessDefinitionKeyColumnFamily;

  public LegacyMessageStartEventSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    messageName = new DbString();
    processDefinitionKey = new DbLong();
    messageNameAndProcessDefinitionKey = new DbCompositeKey<>(messageName, processDefinitionKey);
    subscriptionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
            transactionContext,
            messageNameAndProcessDefinitionKey,
            messageStartEventSubscription);

    processDefinitionKeyAndMessageName = new DbCompositeKey<>(processDefinitionKey, messageName);
    subscriptionsOfProcessDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
            transactionContext,
            processDefinitionKeyAndMessageName,
            DbNil.INSTANCE);
  }

  public void put(final long key, final MessageStartEventSubscriptionRecord subscription) {
    messageStartEventSubscription.setKey(key).setRecord(subscription);

    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());
    subscriptionsColumnFamily.upsert(
        messageNameAndProcessDefinitionKey, messageStartEventSubscription);
    subscriptionsOfProcessDefinitionKeyColumnFamily.upsert(
        processDefinitionKeyAndMessageName, DbNil.INSTANCE);
  }

  public void remove(final long processDefinitionKey, final DirectBuffer messageName) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageName.wrapBuffer(messageName);

    subscriptionsColumnFamily.deleteExisting(messageNameAndProcessDefinitionKey);
    subscriptionsOfProcessDefinitionKeyColumnFamily.deleteExisting(
        processDefinitionKeyAndMessageName);
  }

  public boolean exists(final MessageStartEventSubscriptionRecord subscription) {
    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    return subscriptionsColumnFamily.exists(messageNameAndProcessDefinitionKey);
  }

  public void visitSubscriptionsByProcessDefinition(
      final long processDefinitionKey, final MessageStartEventSubscriptionVisitor visitor) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    subscriptionsOfProcessDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        (key, value) -> {
          final var subscription =
              subscriptionsColumnFamily.get(messageNameAndProcessDefinitionKey);

          if (subscription != null) {
            visitor.visit(subscription);
          }
        });
  }

  public void visitSubscriptionsByMessageName(
      final DirectBuffer messageName, final MessageStartEventSubscriptionVisitor visitor) {

    this.messageName.wrapBuffer(messageName);
    subscriptionsColumnFamily.whileEqualPrefix(
        this.messageName,
        (key, value) -> {
          visitor.visit(value);
        });
  }
}
