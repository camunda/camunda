/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.MessageStartEventSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public interface MessageStartEventSubscriptionState {

  boolean exists(MessageStartEventSubscriptionRecord subscription);

  default void visitSubscriptionsByMessageName(
      final DirectBuffer messageName, final MessageStartEventSubscriptionVisitor visitor) {
    visitSubscriptionsByMessageName(
        messageName, BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID), visitor);
  }

  void visitSubscriptionsByMessageName(
      DirectBuffer messageName,
      DirectBuffer tenantId,
      MessageStartEventSubscriptionVisitor visitor);

  /**
   * Visit all subscriptions with the given process definition key.
   *
   * @param processDefinitionKey the key of the process definition the subscription belongs to
   * @param visitor the function that is called for each subscription
   */
  default void visitSubscriptionsByProcessDefinition(
      final long processDefinitionKey, final MessageStartEventSubscriptionVisitor visitor) {
    visitSubscriptionsByProcessDefinition(
        processDefinitionKey,
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID),
        visitor);
  }

  void visitSubscriptionsByProcessDefinition(
      long processDefinitionKey,
      DirectBuffer tenantId,
      MessageStartEventSubscriptionVisitor visitor);

  @FunctionalInterface
  interface MessageStartEventSubscriptionVisitor {
    void visit(MessageStartEventSubscription subscription);
  }
}
