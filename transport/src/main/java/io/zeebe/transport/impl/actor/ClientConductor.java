/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.actor;

import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.ClientInputMessageSubscription;
import io.zeebe.transport.ClientMessageHandler;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.RemoteAddressList;
import io.zeebe.transport.impl.ClientInputMessageSubscriptionImpl;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.ConnectTransportPoller;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;

public class ClientConductor extends Conductor {
  private final ConnectTransportPoller connectTransportPoller;

  public ClientConductor(ActorContext actorContext, TransportContext context) {
    super(actorContext, context);
    connectTransportPoller = new ConnectTransportPoller();
    remoteAddressList.setOnAddressAddedConsumer(this::onRemoteAddressAdded);
  }

  @Override
  protected void onActorStarted() {
    super.onActorStarted();
    actor.pollBlocking(connectTransportPoller::pollBlocking, connectTransportPoller::processKeys);
  }

  public void openChannel(RemoteAddressImpl address, int connectAttempt) {
    final TransportChannel channel =
        channelFactory.buildClientChannel(
            this,
            address,
            transportContext.getMessageMaxLength(),
            transportContext.getReceiveHandler());

    if (channel.beginConnect(connectAttempt)) {
      // backoff connecton attempts
      actor.runDelayed(
          Duration.ofMillis(Math.min(1000, 50 * connectAttempt)),
          () -> {
            connectTransportPoller.addChannel(channel);
          });

      channels.put(address.getStreamId(), channel);
    }
  }

  @Override
  public void onChannelClosed(TransportChannel channel, boolean wasConnected) {
    // #submit is better than #run here => ensures we yield and make progress on other jobs
    actor.submit(
        () -> {
          final RemoteAddressImpl remoteAddress = channel.getRemoteAddress();

          if (remoteAddress.isActive()) {
            final int openAttempt = channel.getOpenAttempt() + 1;
            openChannel(remoteAddress, openAttempt);
          }

          super.onChannelClosed(channel, wasConnected);
        });
  }

  @Override
  protected void onActorClosing() {
    connectTransportPoller.close();
    super.onActorClosing();
  }

  private void onRemoteAddressAdded(RemoteAddressImpl remoteAddress) {
    actor.call(
        () -> {
          final TransportChannel channel = channels.get(remoteAddress.getStreamId());

          if (channel == null) {
            openChannel(remoteAddress, 0);
          } else {
            if (channel.isClosed()) {
              openChannel(remoteAddress, 0);
            }
          }
        });
  }

  public ActorFuture<ClientInputMessageSubscription> openClientInputMessageSubscription(
      String subscriptionName,
      ClientMessageHandler messageHandler,
      ClientOutput output,
      RemoteAddressList remoteAddressList) {
    final CompletableActorFuture<ClientInputMessageSubscription> future =
        new CompletableActorFuture<>();

    actor.call(
        () -> {
          final ActorFuture<Subscription> subscriptionFuture =
              transportContext.getReceiveBuffer().openSubscriptionAsync(subscriptionName);

          actor.runOnCompletion(
              subscriptionFuture,
              (s, t) -> {
                if (t != null) {
                  future.completeExceptionally(t);
                } else {
                  future.complete(
                      new ClientInputMessageSubscriptionImpl(
                          s, messageHandler, output, remoteAddressList));
                }
              });
        });

    return future;
  }
}
