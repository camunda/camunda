/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_4.legacy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import org.agrona.DirectBuffer;

public class LegacySignalSubscriptionState {

  private final DbString signalName;
  // processDefinitionKey or elementInstanceKey
  private final DbLong subscriptionKey;

  // (signalName, subscriptionKey => SignalSubscription)
  private final DbCompositeKey<DbString, DbLong> signalNameAndSubscriptionKey;
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, SignalSubscription>
      signalNameAndSubscriptionKeyColumnFamily;
  private final SignalSubscription signalSubscription = new SignalSubscription();

  // (subscriptionKey, signalName) => \0  : to find existing subscriptions of a process or element
  private final DbCompositeKey<DbLong, DbString> subscriptionKeyAndSignalName;
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil>
      subscriptionKeyAndSignalNameColumnFamily;

  public LegacySignalSubscriptionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    signalName = new DbString();
    subscriptionKey = new DbLong();
    signalNameAndSubscriptionKey = new DbCompositeKey<>(signalName, subscriptionKey);
    signalNameAndSubscriptionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY,
            transactionContext,
            signalNameAndSubscriptionKey,
            signalSubscription);

    subscriptionKeyAndSignalName = new DbCompositeKey<>(subscriptionKey, signalName);
    subscriptionKeyAndSignalNameColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPRECATED_SIGNAL_SUBSCRIPTION_BY_KEY_AND_NAME,
            transactionContext,
            subscriptionKeyAndSignalName,
            DbNil.INSTANCE);
  }

  public void put(final long key, final SignalSubscriptionRecord subscription) {
    signalSubscription.setKey(key).setRecord(subscription);

    wrapSubscriptionKeys(subscription);

    signalNameAndSubscriptionKeyColumnFamily.upsert(
        signalNameAndSubscriptionKey, signalSubscription);
    subscriptionKeyAndSignalNameColumnFamily.upsert(subscriptionKeyAndSignalName, DbNil.INSTANCE);
  }

  public ColumnFamily<DbCompositeKey<DbString, DbLong>, SignalSubscription>
      getSignalNameAndSubscriptionKeyColumnFamily() {
    return signalNameAndSubscriptionKeyColumnFamily;
  }

  public ColumnFamily<DbCompositeKey<DbLong, DbString>, DbNil>
      getSubscriptionKeyAndSignalNameColumnFamily() {
    return subscriptionKeyAndSignalNameColumnFamily;
  }

  private void wrapSubscriptionKeys(final SignalSubscriptionRecord subscription) {
    final var key = subscription.getSubscriptionKey();
    wrapSubscriptionKeys(key, subscription.getSignalNameBuffer());
  }

  private void wrapSubscriptionKeys(final long key, final DirectBuffer signalName) {
    subscriptionKey.wrapLong(key);
    this.signalName.wrapBuffer(signalName);
  }
}
