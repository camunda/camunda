/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public interface MutableMessageStartEventSubscriptionState
    extends MessageStartEventSubscriptionState {

  void put(final long key, MessageStartEventSubscriptionRecord subscription);

  default void remove(final long processDefinitionKey, final DirectBuffer messageName) {
    remove(
        processDefinitionKey,
        messageName,
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID));
  }

  void remove(long processDefinitionKey, DirectBuffer messageName, final DirectBuffer tenantId);
}
