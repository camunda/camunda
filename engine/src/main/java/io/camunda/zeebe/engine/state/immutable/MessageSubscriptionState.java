/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public interface MessageSubscriptionState {

  MessageSubscription get(long elementInstanceKey, DirectBuffer messageName);

  default void visitSubscriptions(
      final DirectBuffer messageName, final DirectBuffer correlationKey, final MessageSubscriptionVisitor visitor) {
    visitSubscriptions(
        messageName,
        correlationKey,
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID),
        visitor);
  }

  void visitSubscriptions(
      DirectBuffer messageName,
      DirectBuffer correlationKey,
      DirectBuffer tenantId,
      MessageSubscriptionVisitor visitor);

  boolean existSubscriptionForElementInstance(long elementInstanceKey, DirectBuffer messageName);

  @FunctionalInterface
  interface MessageSubscriptionVisitor {
    boolean visit(MessageSubscription subscription);
  }
}
