/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState.MessageSubscriptionVisitor;
import io.camunda.zeebe.engine.state.message.MessageSubscription;

public interface MutablePendingMessageSubscriptionState {

  void visitSubscriptionBefore(long deadline, MessageSubscriptionVisitor visitor);

  void updateSentTimeInTransaction(MessageSubscription subscription, long sentTime);
}
