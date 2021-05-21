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
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;

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
  public void migrateMessageSubscriptionSentTime(
      final MutableMessageSubscriptionState messageSubscriptionState) {

    messageSubscriptionSentTimeColumnFamily.forEach(
        (key, value) -> {
          final var sentTime = key.getFirst().getValue();
          final var elementKeyAndMessageName = key.getSecond();
          final var elementInstanceKey = elementKeyAndMessageName.getFirst().getValue();
          final var messageName = elementKeyAndMessageName.getSecond().getBuffer();

          final var messageSubscription =
              messageSubscriptionState.get(elementInstanceKey, messageName);
          if (messageSubscription != null) {
            messageSubscriptionState.updateToCorrelatingState(
                messageSubscription.getRecord(), sentTime);
          }

          messageSubscriptionSentTimeColumnFamily.delete(key);
        });
  }

  @Override
  public void migrateProcessMessageSubscriptionSentTime(
      final MutableProcessMessageSubscriptionState processMessageSubscriptionState) {

    processSubscriptionSentTimeColumnFamily.forEach(
        (key, value) -> {
          final var sentTime = key.getFirst().getValue();
          final var elementKeyAndMessageName = key.getSecond();
          final var elementInstanceKey = elementKeyAndMessageName.getFirst().getValue();
          final var messageName = elementKeyAndMessageName.getSecond().getBuffer();

          final var processMessageSubscription =
              processMessageSubscriptionState.getSubscription(elementInstanceKey, messageName);
          if (processMessageSubscription != null) {

            final var record = processMessageSubscription.getRecord();
            if (processMessageSubscription.isOpening()) {
              processMessageSubscriptionState.put(elementInstanceKey, record, sentTime);
            } else if (processMessageSubscription.isClosing()) {
              processMessageSubscriptionState.updateToClosingState(record, sentTime);
            }
          }

          processSubscriptionSentTimeColumnFamily.delete(key);
        });
  }
}
