/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport.impl.actor;

import io.zeebe.transport.Loggers;
import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.RemoteAddressListImpl;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportChannel.ChannelLifecycleListener;
import io.zeebe.transport.impl.TransportChannelFactory;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;
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

  private final List<TransportListener> transportListeners = new ArrayList<>();
  protected Int2ObjectHashMap<TransportChannel> channels = new Int2ObjectHashMap<>();

  private final ActorContext actorContext;
  protected final AtomicBoolean closing = new AtomicBoolean(false);
  protected final TransportChannelFactory channelFactory;

  private final Metric activeConnectionsMetric;

  public Conductor(ActorContext actorContext, TransportContext context) {
    this.actorContext = actorContext;
    this.transportContext = context;
    this.remoteAddressList = context.getRemoteAddressList();
    this.channelFactory = context.getChannelFactory();

    actorContext.setConductor(this);

    final MetricsManager metricsManager = actorContext.getMetricsManager();

    activeConnectionsMetric =
        metricsManager
            .newMetric("transport_active_connections")
            .type("gauge")
            .label("transport", transportContext.getName())
            .create();
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
    activeConnectionsMetric.incrementOrdered();

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

  public ActorFuture<Void> interruptAllChannels() {
    return actor.call(
        () -> {
          new ArrayList<>(channels.values()).forEach(TransportChannel::interrupt);
        });
  }

  @Override
  public void onChannelClosed(TransportChannel ch, boolean wasConnected) {
    actor.run(
        () -> {
          if (channels.remove(ch.getRemoteAddress().getStreamId()) != null) {
            activeConnectionsMetric.getAndAddOrdered(-1);
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
          activeConnectionsMetric.close();
        });
  }

  protected void onSenderAndReceiverClosed() {
    // empty
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  public ActorFuture<Void> closeCurrentChannels() {
    return actor.call(
        () -> {
          new ArrayList<>(channels.values()).forEach(TransportChannel::close);
        });
  }
}
