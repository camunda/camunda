/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.state.message.MessageStartEventSubscription;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;

public interface MessageStartEventSubscriptionState {

  boolean exists(MessageStartEventSubscriptionRecord subscription);

  void visitSubscriptionsByMessageName(
      DirectBuffer messageName, MessageStartEventSubscriptionVisitor visitor);

  /**
   * Visit all subscriptions with the given process definition key.
   *
   * @param processDefinitionKey the key of the process definition the subscription belongs to
   * @param visitor the function that is called for each subscription
   */
  void visitSubscriptionsByProcessDefinition(
      long processDefinitionKey, MessageStartEventSubscriptionVisitor visitor);

  @FunctionalInterface
  interface MessageStartEventSubscriptionVisitor {
    void visit(MessageStartEventSubscription subscription);
  }
}
