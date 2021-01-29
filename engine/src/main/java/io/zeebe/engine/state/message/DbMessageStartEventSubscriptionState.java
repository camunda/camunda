/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class DbMessageStartEventSubscriptionState
    implements MutableMessageStartEventSubscriptionState {

  private final DbString messageName;
  private final DbLong workflowKey;

  // (messageName, workflowKey => MessageSubscription)
  private final DbCompositeKey<DbString, DbLong> messageNameAndWorkflowKey;
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, SubscriptionValue>
      subscriptionsColumnFamily;
  private final SubscriptionValue subscriptionValue = new SubscriptionValue();

  // (workflowKey, messageName) => \0  : to find existing subscriptions of a workflow
  private final DbCompositeKey<DbLong, DbString> workflowKeyAndMessageName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil>
      subscriptionsOfWorkflowKeyColumnFamily;

  public DbMessageStartEventSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    messageName = new DbString();
    workflowKey = new DbLong();
    messageNameAndWorkflowKey = new DbCompositeKey<>(messageName, workflowKey);
    subscriptionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
            transactionContext,
            messageNameAndWorkflowKey,
            subscriptionValue);

    workflowKeyAndMessageName = new DbCompositeKey<>(workflowKey, messageName);
    subscriptionsOfWorkflowKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
            transactionContext,
            workflowKeyAndMessageName,
            DbNil.INSTANCE);
  }

  @Override
  public void put(final MessageStartEventSubscriptionRecord subscription) {
    subscriptionValue.set(subscription);

    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    workflowKey.wrapLong(subscription.getWorkflowKey());
    subscriptionsColumnFamily.put(messageNameAndWorkflowKey, subscriptionValue);
    subscriptionsOfWorkflowKeyColumnFamily.put(workflowKeyAndMessageName, DbNil.INSTANCE);
  }

  @Override
  public void removeSubscriptionsOfWorkflow(final long workflowKey) {
    this.workflowKey.wrapLong(workflowKey);

    subscriptionsOfWorkflowKeyColumnFamily.whileEqualPrefix(
        this.workflowKey,
        (key, value) -> {
          subscriptionsColumnFamily.delete(messageNameAndWorkflowKey);
          subscriptionsOfWorkflowKeyColumnFamily.delete(key);
        });
  }

  @Override
  public boolean exists(final MessageStartEventSubscriptionRecord subscription) {
    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    workflowKey.wrapLong(subscription.getWorkflowKey());

    return subscriptionsColumnFamily.exists(messageNameAndWorkflowKey);
  }

  @Override
  public void visitSubscriptionsByMessageName(
      final DirectBuffer messageName, final Consumer<MessageStartEventSubscriptionRecord> visitor) {

    this.messageName.wrapBuffer(messageName);
    subscriptionsColumnFamily.whileEqualPrefix(
        this.messageName,
        (key, value) -> {
          visitor.accept(value.get());
        });
  }
}
