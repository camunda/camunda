/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.signal;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableSignalSubscriptionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import org.agrona.DirectBuffer;

public final class DbSignalSubscriptionState implements MutableSignalSubscriptionState {

  private final DbString signalName;
  private final DbLong processDefinitionKey;

  // (signalName, processDefinitionKey => SignalSubscription)
  private final DbCompositeKey<DbString, DbLong> signalNameAndProcessDefinitionKey;
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, SignalSubscription>
      subscriptionsColumnFamily;
  private final SignalSubscription signalSubscription = new SignalSubscription();

  // (processDefinitionKey, signalName) => \0  : to find existing subscriptions of a process
  private final DbCompositeKey<DbLong, DbString> processDefinitionKeyAndSignalName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil>
      subscriptionsOfProcessDefinitionKeyColumnFamily;

  public DbSignalSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    signalName = new DbString();
    processDefinitionKey = new DbLong();
    signalNameAndProcessDefinitionKey = new DbCompositeKey<>(signalName, processDefinitionKey);
    subscriptionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
            transactionContext,
            signalNameAndProcessDefinitionKey,
            signalSubscription);

    processDefinitionKeyAndSignalName = new DbCompositeKey<>(processDefinitionKey, signalName);
    subscriptionsOfProcessDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
            transactionContext,
            processDefinitionKeyAndSignalName,
            DbNil.INSTANCE);
  }

  @Override
  public void put(final long key, final SignalSubscriptionRecord subscription) {
    signalSubscription.setKey(key).setRecord(subscription);

    signalName.wrapBuffer(subscription.getSignalNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());
    subscriptionsColumnFamily.upsert(signalNameAndProcessDefinitionKey, signalSubscription);
    subscriptionsOfProcessDefinitionKeyColumnFamily.upsert(
        processDefinitionKeyAndSignalName, DbNil.INSTANCE);
  }

  @Override
  public void remove(final long processDefinitionKey, final DirectBuffer signalName) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);
    this.signalName.wrapBuffer(signalName);

    subscriptionsColumnFamily.deleteExisting(signalNameAndProcessDefinitionKey);
    subscriptionsOfProcessDefinitionKeyColumnFamily.deleteExisting(
        processDefinitionKeyAndSignalName);
  }

  @Override
  public boolean exists(final SignalSubscriptionRecord subscription) {
    signalName.wrapBuffer(subscription.getSignalNameBuffer());
    processDefinitionKey.wrapLong(subscription.getProcessDefinitionKey());

    return subscriptionsColumnFamily.exists(signalNameAndProcessDefinitionKey);
  }

  @Override
  public void visitBySignalName(
      final DirectBuffer signalName, final SignalSubscriptionVisitor visitor) {
    this.signalName.wrapBuffer(signalName);
    subscriptionsColumnFamily.whileEqualPrefix(
        this.signalName,
        (key, value) -> {
          visitor.visit(value);
        });
  }

  @Override
  public void visitStartEventSubscriptionsByProcessDefinitionKey(
      final long processDefinitionKey, final SignalSubscriptionVisitor visitor) {
    this.processDefinitionKey.wrapLong(processDefinitionKey);

    subscriptionsOfProcessDefinitionKeyColumnFamily.whileEqualPrefix(
        this.processDefinitionKey,
        (key, value) -> {
          final var subscription = subscriptionsColumnFamily.get(signalNameAndProcessDefinitionKey);

          if (subscription != null) {
            visitor.visit(subscription);
          }
        });
  }
}
