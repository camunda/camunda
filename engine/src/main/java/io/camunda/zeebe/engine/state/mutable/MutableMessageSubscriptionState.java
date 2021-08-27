/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import org.agrona.DirectBuffer;

public interface MutableMessageSubscriptionState extends MessageSubscriptionState {

  void put(long key, MessageSubscriptionRecord record);

  void updateToCorrelatingState(MessageSubscriptionRecord record);

  void updateToCorrelatedState(MessageSubscription subscription);

  boolean remove(long elementInstanceKey, DirectBuffer messageName);

  void remove(MessageSubscription subscription);
}
