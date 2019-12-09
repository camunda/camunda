/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.actor;

import io.zeebe.transport.Loggers;
import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.RemoteAddressListImpl;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportChannel.ChannelLifecycleListener;
import io.zeebe.transport.impl.TransportChannelFactory;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorThread;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

public abstract class Conductor extends Actor implements ChannelLifecycleListener {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  protected final RemoteAddressListImpl remoteAddressList;
  protected final TransportContext transportContext;
  protected final AtomicBoolean closing = new AtomicBoolean(false);
  protected final TransportChannelFactory channelFactory;
  protected Int2ObjectHashMap<TransportChannel> channels = new Int2ObjectHashMap<>();
  private final List<TransportListener> transportListeners = new ArrayList<>();
  private final ActorContext actorContext;

  public Conductor(ActorContext actorContext, TransportContext context) {
    this.actorContext = actorContext;
    this.transportContext = context;
    this.remoteAddressList = context.getRemoteAddressList();
    this.channelFactory = context.getChannelFactory();

    actorContext.setConductor(this);
  }

  public ActorFuture<Void> registerListener(TransportListener channelListener) {
    return actor.call(
        () -> {
          transportListeners.add(channelListener);
        });
  }

  public void removeListener(TransportListener channelListener) {
    // TODO make better
    if (ActorThread.current() != null) {
      actor.submit(
          () -> {
            transportListeners.remove(channelListener);
          });
    } else {
      actor.call(
          () -> {
            transportListeners.remove(channelListener);
          });
    }
  }

  // channel lifecycle

  @Override
  public void onChannelConnected(TransportChannel ch) {
    channels.put(ch.getRemoteAddress().getStreamId(), ch);

    final ActorFuture<Void> f1 = actorContext.getReceiver().registerChannel(ch);
    final ActorFuture<Void> f2 = actorContext.getSender().onChannelConnected(ch);

    actor.runOnCompletion(
        Arrays.asList(f1, f2),
        (t) -> {
          transportListeners.forEach(
              l -> {
                try {
                  l.onConnectionEstablished(ch.getRemoteAddress());
                } catch (Exception e) {
                  LOG.debug("Failed to call transport listener {} on channel connect", l, e);
                }
              });
        });
  }

  @Override
  public void onChannelClosed(TransportChannel ch, boolean wasConnected) {
    actor.run(
        () -> {
          if (channels.remove(ch.getRemoteAddress().getStreamId()) != null) {
            if (wasConnected) {
              failRequestsOnChannel(ch, "Socket channel has been disconnected");
              final ActorFuture<Void> f1 = actorContext.getReceiver().removeChannel(ch);
              final ActorFuture<Void> f2 = actorContext.getSender().onChannelClosed(ch);

              // wait for deregistration in order to not mix up the order of listener callbacks
              actor.runOnCompletion(
                  Arrays.asList(f1, f2),
                  t -> {
                    transportListeners.forEach(
                        l -> {
                          try {
                            l.onConnectionClosed(ch.getRemoteAddress());
                          } catch (Exception e) {
                            LOG.debug("Failed to call transport listener {} on disconnect", l, e);
                          }
                        });
                  });
            }
          }
        });
  }

  public ActorFuture<Void> interruptAllChannels() {
    return actor.call(
        () -> {
          new ArrayList<>(channels.values()).forEach(TransportChannel::interrupt);
        });
  }

  protected void failRequestsOnChannel(TransportChannel ch, String reason) {
    actorContext.getSender().failPendingRequestsToRemote(ch.getRemoteAddress(), reason);
  }

  @Override
  protected void onActorClosing() {
    remoteAddressList.deactivateAll();

    new ArrayList<>(channels.values()).forEach(TransportChannel::close);

    final ActorFuture<Void> senderClose = actorContext.closeSender();
    final ActorFuture<Void> receiverClose = actorContext.closeReceiver();

    actor.runOnCompletion(
        Arrays.asList(senderClose, receiverClose),
        (t) -> {
          onSenderAndReceiverClosed();
        });
  }

  protected void onSenderAndReceiverClosed() {
    // empty
  }

  public ActorFuture<Void> closeCurrentChannels() {
    return actor.call(
        () -> {
          new ArrayList<>(channels.values()).forEach(TransportChannel::close);
        });
  }
}
