/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;

public interface MutableMessageStartEventSubscriptionState
    extends MessageStartEventSubscriptionState {

  void put(final long key, MessageStartEventSubscriptionRecord subscription);

  void remove(long processDefinitionKey, DirectBuffer messageName, final String tenantId);
}
