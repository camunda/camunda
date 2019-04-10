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
package io.zeebe.transport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ClientTransport implements AutoCloseable {

  public static final int UNKNOWN_NODE_ID = -1;

  private final ClientOutput output;
  private final RemoteAddressList remoteAddressList;
  private final EndpointRegistry endpointRegistry;
  private final ActorContext transportActorContext;
  private final Dispatcher receiveBuffer;
  private final TransportContext transportContext;

  public ClientTransport(ActorContext transportActorContext, TransportContext transportContext) {
    this.transportActorContext = transportActorContext;
    this.transportContext = transportContext;
    this.output = transportContext.getClientOutput();
    this.remoteAddressList = transportContext.getRemoteAddressList();
    this.endpointRegistry = transportContext.getEndpointRegistry();
    this.receiveBuffer = transportContext.getReceiveBuffer();
  }

  /** @return interface to stage outbound data */
  public ClientOutput getOutput() {
    return output;
  }

  /**
   * Register an endpoint address for node id. Transport will make sure to keep an open channel to
   * this endpoint until it is deactivated or retired.
   */
  public void registerEndpoint(int nodeId, SocketAddress socketAddress) {
    endpointRegistry.setEndpoint(nodeId, socketAddress);
  }

  public RemoteAddress getEndpoint(int nodeId) {
    return endpointRegistry.getEndpoint(nodeId);
  }

  /**
   * Signals that the endpoint of the node is no longer in use for the time being. A transport
   * channel will no longer be managed. A endpoint is reactivated when the endpoint is registered
   * again.
   */
  public void deactivateEndpoint(int nodeId) {
    endpointRegistry.removeEndpoint(nodeId);
  }

  /**
   * Signals that the endpoint is no longer used and that the stream should not be reused on
   * reactivation. That means, when the endpoint is registered again, it is assigned a different
   * stream id.
   */
  public void retireEndpoint(int nodeId) {
    endpointRegistry.retire(nodeId);
  }

  /**
   * DO NOT USE in production code as it involves blocking the current thread.
   *
   * <p>Not thread-safe
   *
   * <p>Like {@link #registerEndpoint(int, SocketAddress)} but blockingly waits for the
   * corresponding channel to be opened such that it is probable that subsequent requests/messages
   * can be sent. This saves test code the need to retry sending.
   */
  public void registerEndpointAndAwaitChannel(final int nodeId, SocketAddress addr) {
    final RemoteAddress remoteAddress = getRemoteAddress(addr);

    if (remoteAddress == null) {
      final Lock lock = new ReentrantLock();
      final Condition connectionEstablished = lock.newCondition();

      lock.lock();
      try {
        final TransportListener listener =
            new TransportListener() {
              @Override
              public void onConnectionEstablished(RemoteAddress remoteAddress) {
                lock.lock();
                try {
                  if (remoteAddress.getAddress().equals(addr)) {
                    connectionEstablished.signal();
                    removeChannelListener(this);
                  }
                } finally {
                  lock.unlock();
                }
              }

              @Override
              public void onConnectionClosed(RemoteAddress remoteAddress) {}
            };

        transportActorContext.registerListener(listener).join();

        registerEndpoint(nodeId, addr);
        try {
          if (!connectionEstablished.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException(new TimeoutException());
          }
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      } finally {
        lock.unlock();
      }
    }
  }

  private RemoteAddress getRemoteAddress(SocketAddress addr) {
    return remoteAddressList.getByAddress(addr);
  }

  /**
   * Creates a subscription on the receive buffer for single messages.
   *
   * @throws RuntimeException if this client was not created with a receive buffer for
   *     single-messages
   */
  public ActorFuture<ClientInputMessageSubscription> openSubscription(
      String subscriptionName, ClientMessageHandler messageHandler) {
    if (receiveBuffer == null) {
      throw new RuntimeException("Cannot throw exception. No receive buffer in use");
    }

    return transportActorContext
        .getClientConductor()
        .openClientInputMessageSubscription(
            subscriptionName, messageHandler, output, remoteAddressList);
  }

  /**
   * Registers a listener with callbacks for whenever a connection to a remote gets established or
   * closed.
   */
  public ActorFuture<Void> registerChannelListener(TransportListener channelListener) {
    return transportActorContext.registerListener(channelListener);
  }

  public void removeChannelListener(TransportListener listener) {
    transportActorContext.removeListener(listener);
  }

  public ActorFuture<Void> closeAsync() {
    return transportActorContext.onClose();
  }

  @Override
  public void close() {
    closeAsync().join();
  }

  public void interruptAllChannels() {
    transportActorContext.interruptAllChannels();
  }

  public ActorFuture<Void> closeAllChannels() {
    return transportActorContext.closeAllOpenChannels();
  }

  public Duration getChannelKeepAlivePeriod() {
    return transportContext.getChannelKeepAlivePeriod();
  }
}
