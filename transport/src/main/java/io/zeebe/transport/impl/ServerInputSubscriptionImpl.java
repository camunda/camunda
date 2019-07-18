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
import io.zeebe.transport.RemoteAddressList;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.util.sched.ActorCondition;

public class ServerInputSubscriptionImpl implements ServerInputSubscription {
  protected final Subscription subscription;
  protected final FragmentHandler fragmentHandler;

  public ServerInputSubscriptionImpl(
      ServerOutput output,
      Subscription subscription,
      RemoteAddressList addressList,
      ServerMessageHandler messageHandler,
      ServerRequestHandler requestHandler) {
    this.subscription = subscription;
    this.fragmentHandler =
        new ServerReceiveHandler(output, addressList, messageHandler, requestHandler, null);
  }

  @Override
  public int poll() {
    return poll(Integer.MAX_VALUE);
  }

  @Override
  public int poll(int maxCount) {
    return subscription.poll(fragmentHandler, maxCount);
  }

  @Override
  public boolean hasAvailable() {
    return subscription.hasAvailable();
  }

  @Override
  public void registerConsumer(ActorCondition onDataAvailable) {
    subscription.registerConsumer(onDataAvailable);
  }

  @Override
  public void removeConsumer(ActorCondition onDataAvailable) {
    subscription.removeConsumer(onDataAvailable);
  }
}
