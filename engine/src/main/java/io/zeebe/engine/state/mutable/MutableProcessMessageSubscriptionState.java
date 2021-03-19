/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.ProcessMessageSubscriptionState;
import io.zeebe.engine.state.message.ProcessMessageSubscription;
import org.agrona.DirectBuffer;

public interface MutableProcessMessageSubscriptionState extends ProcessMessageSubscriptionState {

  void put(ProcessMessageSubscription subscription);

  void updateToOpenedState(ProcessMessageSubscription subscription, int subscriptionPartitionId);

  void updateToClosingState(ProcessMessageSubscription subscription, long sentTime);

  void updateSentTimeInTransaction(ProcessMessageSubscription subscription, long sentTime);

  void updateSentTime(ProcessMessageSubscription subscription, long sentTime);

  boolean remove(long elementInstanceKey, DirectBuffer messageName);

  void remove(ProcessMessageSubscription subscription);
}
