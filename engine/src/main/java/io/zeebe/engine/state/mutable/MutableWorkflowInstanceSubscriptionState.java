/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.WorkflowInstanceSubscriptionState;
import io.zeebe.engine.state.message.WorkflowInstanceSubscription;
import org.agrona.DirectBuffer;

public interface MutableWorkflowInstanceSubscriptionState
    extends WorkflowInstanceSubscriptionState {

  void put(WorkflowInstanceSubscription subscription);

  void updateToOpenedState(WorkflowInstanceSubscription subscription, int subscriptionPartitionId);

  void updateToClosingState(WorkflowInstanceSubscription subscription, long sentTime);

  void updateSentTimeInTransaction(WorkflowInstanceSubscription subscription, long sentTime);

  void updateSentTime(WorkflowInstanceSubscription subscription, long sentTime);

  boolean remove(long elementInstanceKey, DirectBuffer messageName);

  void remove(WorkflowInstanceSubscription subscription);
}
