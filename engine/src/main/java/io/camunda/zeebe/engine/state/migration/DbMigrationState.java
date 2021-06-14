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
import io.camunda.zeebe.engine.state.mutable.MutableMigrationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableTransientProcessMessageSubscriptionState;

public class DbMigrationState implements MutableMigrationState {

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
  public void migrateProcessMessageSubscriptionSentTime(
      final MutableProcessMessageSubscriptionState persistentSate,
      final MutableTransientProcessMessageSubscriptionState transientState) {

    processSubscriptionSentTimeColumnFamily.forEach(
        (key, value) -> {
          final var sentTime = key.getFirst().getValue();
          final var elementKeyAndMessageName = key.getSecond();
          final var elementInstanceKey = elementKeyAndMessageName.getFirst().getValue();
          final var messageName = elementKeyAndMessageName.getSecond().getBuffer();

          final var processMessageSubscription =
              persistentSate.getSubscription(elementInstanceKey, messageName);
          if (processMessageSubscription != null) {

            final var record = processMessageSubscription.getRecord();
            if (processMessageSubscription.isOpening()) {
              persistentSate.put(elementInstanceKey, record);
              transientState.updateSentTime(record, sentTime);
            } else if (processMessageSubscription.isClosing()) {
              persistentSate.updateToClosingState(record);
              transientState.updateSentTime(record, sentTime);
            }
          }

          processSubscriptionSentTimeColumnFamily.delete(key);
        });
  }
}
