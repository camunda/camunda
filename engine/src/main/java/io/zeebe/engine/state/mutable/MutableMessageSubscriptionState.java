/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.mutable;

import io.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.zeebe.engine.state.message.MessageSubscription;
import org.agrona.DirectBuffer;

public interface MutableMessageSubscriptionState extends MessageSubscriptionState {

  void put(MessageSubscription subscription);

  void updateToCorrelatingState(
      MessageSubscription subscription,
      DirectBuffer messageVariables,
      long sentTime,
      long messageKey);

  void resetSentTime(MessageSubscription subscription);

  void updateSentTimeInTransaction(MessageSubscription subscription, long sentTime);

  void updateSentTime(MessageSubscription subscription, long sentTime);

  boolean remove(long elementInstanceKey, DirectBuffer messageName);

  void remove(MessageSubscription subscription);
}
