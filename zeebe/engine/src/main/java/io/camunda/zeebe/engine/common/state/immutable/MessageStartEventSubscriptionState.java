/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

import io.camunda.zeebe.engine.common.state.message.MessageStartEventSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;

public interface MessageStartEventSubscriptionState {

  boolean exists(MessageStartEventSubscriptionRecord subscription);

  void visitSubscriptionsByMessageName(
      final String tenantId,
      DirectBuffer messageName,
      MessageStartEventSubscriptionVisitor visitor);

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
