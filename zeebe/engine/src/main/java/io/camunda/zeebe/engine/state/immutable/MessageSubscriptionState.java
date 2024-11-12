/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.MessageSubscription;
import org.agrona.DirectBuffer;

public interface MessageSubscriptionState {

  MessageSubscription get(long elementInstanceKey, DirectBuffer messageName);

  /**
   * Visits the message subscriptions that match a given tenant, message name, and correlation key.
   */
  void visitSubscriptions(
      final String tenantId,
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      MessageSubscriptionVisitor visitor);

  boolean existSubscriptionForElementInstance(long elementInstanceKey, DirectBuffer messageName);

  @FunctionalInterface
  interface MessageSubscriptionVisitor {
    boolean visit(MessageSubscription subscription);
  }
}
