/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.state.message.MessageSubscription;
import org.agrona.DirectBuffer;

public interface MessageSubscriptionState {

  MessageSubscription get(long elementInstanceKey, DirectBuffer messageName);

  void visitSubscriptions(
      DirectBuffer messageName, DirectBuffer correlationKey, MessageSubscriptionVisitor visitor);

  void visitSubscriptionBefore(long deadline, MessageSubscriptionVisitor visitor);

  boolean existSubscriptionForElementInstance(long elementInstanceKey, DirectBuffer messageName);

  @FunctionalInterface
  interface MessageSubscriptionVisitor {
    boolean visit(MessageSubscription subscription);
  }
}
