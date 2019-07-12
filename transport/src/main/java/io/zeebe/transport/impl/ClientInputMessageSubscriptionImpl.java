/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.ClientInputMessageSubscription;
import io.zeebe.transport.ClientMessageHandler;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RemoteAddressList;
import io.zeebe.util.sched.ActorCondition;

public class ClientInputMessageSubscriptionImpl implements ClientInputMessageSubscription {
  protected final Subscription subscription;
  protected final FragmentHandler messageHandler;

  public ClientInputMessageSubscriptionImpl(
      Subscription subscription,
      ClientMessageHandler messageHandler,
      ClientOutput output,
      RemoteAddressList remoteAddresses) {
    this.subscription = subscription;
    this.messageHandler =
        (buffer, offset, length, streamId, isMarkedFailed) -> {
          final RemoteAddress remoteAddress = remoteAddresses.getByStreamId(streamId);
          final boolean success =
              messageHandler.onMessage(output, remoteAddress, buffer, offset, length);

          return success
              ? FragmentHandler.CONSUME_FRAGMENT_RESULT
              : FragmentHandler.POSTPONE_FRAGMENT_RESULT;
        };
  }

  @Override
  public int poll() {
    return subscription.peekAndConsume(messageHandler, Integer.MAX_VALUE);
  }

  @Override
  public boolean hasAvailable() {
    return subscription.hasAvailable();
  }

  @Override
  public void registerConsumer(ActorCondition listener) {
    subscription.registerConsumer(listener);
  }

  @Override
  public void removeConsumer(ActorCondition listener) {
    subscription.removeConsumer(listener);
  }
}
