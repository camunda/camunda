/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.message;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.DbString;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.instance.UnpackedObjectValue;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;

public class MessageStartEventSubscriptionState {

  private final DbString messageName;
  private final DbLong workflowKey;

  // (messageName, workflowKey => MessageSubscription)
  private final DbCompositeKey<DbString, DbLong> messageNameAndWorkflowKey;
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, UnpackedObjectValue>
      subscriptionsColumnFamily;
  private final UnpackedObjectValue subscriptionValue;
  private final MessageStartEventSubscriptionRecord subscriptionRecord;

  // (workflowKey, messageName) => \0  : to find existing subscriptions of a workflow
  private final DbCompositeKey<DbLong, DbString> workflowKeyAndMessageName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil>
      subscriptionsOfWorkflowKeyColumnfamily;

  public MessageStartEventSubscriptionState(
      ZeebeDb<ZbColumnFamilies> zeebeDb, DbContext dbContext) {
    messageName = new DbString();
    workflowKey = new DbLong();
    messageNameAndWorkflowKey = new DbCompositeKey<>(messageName, workflowKey);
    subscriptionValue = new UnpackedObjectValue();
    subscriptionRecord = new MessageStartEventSubscriptionRecord();
    subscriptionValue.wrapObject(subscriptionRecord);
    subscriptionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
            dbContext,
            messageNameAndWorkflowKey,
            subscriptionValue);

    workflowKeyAndMessageName = new DbCompositeKey<>(workflowKey, messageName);
    subscriptionsOfWorkflowKeyColumnfamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,
            dbContext,
            workflowKeyAndMessageName,
            DbNil.INSTANCE);
  }

  public void put(final MessageStartEventSubscriptionRecord subscription) {
    subscriptionRecord.setStartEventId(subscription.getStartEventIdBuffer());
    subscriptionRecord.setMessageName(subscription.getMessageNameBuffer());
    subscriptionRecord.setWorkflowKey(subscription.getWorkflowKey());

    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    workflowKey.wrapLong(subscription.getWorkflowKey());
    subscriptionsColumnFamily.put(messageNameAndWorkflowKey, subscriptionValue);
    subscriptionsOfWorkflowKeyColumnfamily.put(workflowKeyAndMessageName, DbNil.INSTANCE);
  }

  public void removeSubscriptionsOfWorkflow(long workflowKey) {
    this.workflowKey.wrapLong(workflowKey);

    subscriptionsOfWorkflowKeyColumnfamily.whileEqualPrefix(
        this.workflowKey,
        (key, value) -> {
          subscriptionsColumnFamily.delete(messageNameAndWorkflowKey);
          subscriptionsOfWorkflowKeyColumnfamily.delete(key);
        });
  }

  public boolean exists(final MessageStartEventSubscriptionRecord subscription) {
    messageName.wrapBuffer(subscription.getMessageNameBuffer());
    workflowKey.wrapLong(subscription.getWorkflowKey());

    return subscriptionsColumnFamily.exists(messageNameAndWorkflowKey);
  }

  public void visitSubscriptionsByMessageName(
      DirectBuffer messageName, MessageStartEventSubscriptionVisitor visitor) {

    this.messageName.wrapBuffer(messageName);
    subscriptionsColumnFamily.whileEqualPrefix(
        this.messageName,
        (key, value) -> {
          visitor.visit((MessageStartEventSubscriptionRecord) value.getObject());
        });
  }

  @FunctionalInterface
  public interface MessageStartEventSubscriptionVisitor {
    void visit(MessageStartEventSubscriptionRecord subscription);
  }
}
