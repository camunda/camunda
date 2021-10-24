/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;

public class DbMigrationState implements MutableMigrationState {

  // ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME
  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong messageSubscriptionSentTime;
  private final DbLong messageSubscriptionElementInstanceKey;
  private final DbString messageSubscriptionMessageName;

  private final DbCompositeKey<DbLong, DbString> messageSubscriptionElementKeyAndMessageName;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>
      messageSubscriptionSentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      messageSubscriptionSentTimeColumnFamily;

  // ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME,
  // (sentTime, elementInstanceKey, messageName) => \0
  private final DbLong processSubscriptionSentTime;
  private final DbLong processSubscriptionElementInstanceKey;
  private final DbString processSubscriptionMessageName;

  private final DbCompositeKey<DbLong, DbString> processSubscriptionElementKeyAndMessageName;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>
      processSubscriptionSentTimeCompositeKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbCompositeKey<DbLong, DbString>>, DbNil>
      processSubscriptionSentTimeColumnFamily;

  public DbMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    messageSubscriptionElementInstanceKey = new DbLong();
    messageSubscriptionMessageName = new DbString();
    messageSubscriptionElementKeyAndMessageName =
        new DbCompositeKey<>(messageSubscriptionElementInstanceKey, messageSubscriptionMessageName);

    messageSubscriptionSentTime = new DbLong();
    messageSubscriptionSentTimeCompositeKey =
        new DbCompositeKey<>(
            messageSubscriptionSentTime, messageSubscriptionElementKeyAndMessageName);
    messageSubscriptionSentTimeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
            transactionContext,
            messageSubscriptionSentTimeCompositeKey,
            DbNil.INSTANCE);

    processSubscriptionElementInstanceKey = new DbLong();
    processSubscriptionMessageName = new DbString();
    processSubscriptionElementKeyAndMessageName =
        new DbCompositeKey<>(processSubscriptionElementInstanceKey, processSubscriptionMessageName);

    processSubscriptionSentTime = new DbLong();
    processSubscriptionSentTimeCompositeKey =
        new DbCompositeKey<>(
            processSubscriptionSentTime, processSubscriptionElementKeyAndMessageName);
    processSubscriptionSentTimeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_SUBSCRIPTION_BY_SENT_TIME,
            transactionContext,
            processSubscriptionSentTimeCompositeKey,
            DbNil.INSTANCE);
  }

  @Override
  public synchronized void migrateMessageSubscriptionSentTime(
      final MutableMessageSubscriptionState messageSubscriptionState,
      final MutablePendingMessageSubscriptionState transientState) {

    messageSubscriptionSentTimeColumnFamily.forEach(
        (key, value) -> {
          final var sentTime = key.getFirst().getValue();
          final var elementKeyAndMessageName = key.getSecond();
          final var elementInstanceKey = elementKeyAndMessageName.getFirst().getValue();
          final var messageName = elementKeyAndMessageName.getSecond().getBuffer();

          final var messageSubscription =
              messageSubscriptionState.get(elementInstanceKey, messageName);
          if (messageSubscription != null) {
            messageSubscriptionState.updateToCorrelatingState(messageSubscription.getRecord());
            transientState.updateCommandSentTime(messageSubscription.getRecord(), sentTime);
          }

          messageSubscriptionSentTimeColumnFamily.delete(key);
        });
  }

  @Override
  public synchronized void migrateProcessMessageSubscriptionSentTime(
      final MutableProcessMessageSubscriptionState persistentState,
      final MutablePendingProcessMessageSubscriptionState transientState) {

    processSubscriptionSentTimeColumnFamily.forEach(
        (key, value) -> {
          final var sentTime = key.getFirst().getValue();
          final var elementKeyAndMessageName = key.getSecond();
          final var elementInstanceKey = elementKeyAndMessageName.getFirst().getValue();
          final var messageName = elementKeyAndMessageName.getSecond().getBuffer();

          final var processMessageSubscription =
              persistentState.getSubscription(elementInstanceKey, messageName);
          if (processMessageSubscription != null) {

            final var record = processMessageSubscription.getRecord();

            final ProcessMessageSubscriptionRecord exclusiveCopy =
                new ProcessMessageSubscriptionRecord();
            exclusiveCopy.wrap(record);

            if (processMessageSubscription.isOpening()) {
              // explicit call to put(..). This has the desired side-effect that the subscription
              // is added to transient state
              persistentState.put(elementInstanceKey, exclusiveCopy);
              transientState.updateSentTime(exclusiveCopy, sentTime);
            } else if (processMessageSubscription.isClosing()) {
              // explicit call to updateToClosingState(..). This has the desired side-effect that
              // the subscription is added to transient state
              persistentState.updateToClosingState(exclusiveCopy);
              transientState.updateSentTime(exclusiveCopy, sentTime);
            }
          }

          processSubscriptionSentTimeColumnFamily.delete(key);
        });
  }
}
