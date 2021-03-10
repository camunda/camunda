/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.ProcessInstanceSubscriptionState;
import io.zeebe.engine.state.message.ProcessInstanceSubscription;
import org.agrona.DirectBuffer;

public interface MutableProcessInstanceSubscriptionState extends ProcessInstanceSubscriptionState {

  void put(ProcessInstanceSubscription subscription);

  void updateToOpenedState(ProcessInstanceSubscription subscription, int subscriptionPartitionId);

  void updateToClosingState(ProcessInstanceSubscription subscription, long sentTime);

  void updateSentTimeInTransaction(ProcessInstanceSubscription subscription, long sentTime);

  void updateSentTime(ProcessInstanceSubscription subscription, long sentTime);

  boolean remove(long elementInstanceKey, DirectBuffer messageName);

  void remove(ProcessInstanceSubscription subscription);
}
