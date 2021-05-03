/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.message;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;

public final class DbMessageStartEventSubscriptionState
    implements MutableMessageStartEventSubscriptionState {

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

  public DbMessageStartEventSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    messageName = new DbString();
    processDefinitionKey = new DbLong();
    messageNameAndProcessDefinitionKey = new DbCompositeKey<>(messageName, processDefinitionKey);
    subscriptionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
            transactionContext,
            messageNameAndProcessDefinitionKey,
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

    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());
    subscriptionsColumnFamily.put(
        messageNameAndProcessDefinitionKey, messageStartEventSubscription);
    subscriptionsOfProcessDefinitionKeyColumnFamily.put(
        processDefinitionKeyAndMessageName, DbNil.INSTANCE);
  }

  @Override
  public void remove(final long processDefinitionKey, final DirectBuffer messageName) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.messageName.wrapBuffer(messageName);

    subscriptionsColumnFamily.delete(messageNameAndProcessDefinitionKey);
    subscriptionsOfProcessDefinitionKeyColumnFamily.delete(processDefinitionKeyAndMessageName);
  }

  @Override
  public boolean exists(final MessageStartEventSubscriptionRecord subscription) {
    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    return subscriptionsColumnFamily.exists(messageNameAndProcessDefinitionKey);
  }

  @Override
  public void visitSubscriptionsByMessageName(
      final DirectBuffer messageName, final MessageStartEventSubscriptionVisitor visitor) {

    this.messageName.wrapBuffer(messageName);
    subscriptionsColumnFamily.whileEqualPrefix(
        this.messageName,
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
          final var subscription =
              subscriptionsColumnFamily.get(messageNameAndProcessDefinitionKey);

          if (subscription != null) {
            visitor.visit(subscription);
          }
        });
  }
}
