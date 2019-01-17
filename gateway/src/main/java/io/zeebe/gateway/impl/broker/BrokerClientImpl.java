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
package io.zeebe.gateway.impl.broker;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.gateway.impl.broker.request.BrokerRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ClientTransportBuilder;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.memory.NonBlockingMemoryPool;
import io.zeebe.transport.impl.memory.UnboundedMemoryPool;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class BrokerClientImpl implements BrokerClient {
  public static final Logger LOG = Loggers.GATEWAY_LOGGER;

  protected final ActorScheduler actorScheduler;
  private final Dispatcher dataFrameReceiveBuffer;
  protected final ClientTransport transport;
  private final ClientTransport internalTransport;
  private final BrokerRequestManager requestManager;
  protected final BrokerTopologyManagerImpl topologyManager;

  protected boolean isClosed;

  public BrokerClientImpl(final GatewayCfg configuration) {
    this(configuration, null);
  }

  public BrokerClientImpl(final GatewayCfg configuration, final ActorClock actorClock) {

    this.actorScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(configuration.getThreads().getManagementThreads())
            .setIoBoundActorThreadCount(0)
            .setActorClock(actorClock)
            .setSchedulerName("gateway")
            .build();
    this.actorScheduler.start();

    final ByteValue transportBufferSize = configuration.getCluster().getTransportBuffer();

    dataFrameReceiveBuffer =
        Dispatchers.create("gateway-receive-buffer")
            .bufferSize(transportBufferSize)
            .modePubSub()
            .frameMaxLength(1024 * 1024)
            .actorScheduler(actorScheduler)
            .build();

    final ClientTransportBuilder transportBuilder =
        Transports.newClientTransport("broker-client")
            .messageMaxLength(1024 * 1024)
            .messageReceiveBuffer(dataFrameReceiveBuffer)
            .messageMemoryPool(
                new UnboundedMemoryPool()) // Client is not sending any heavy messages
            .requestMemoryPool(new NonBlockingMemoryPool(transportBufferSize))
            .scheduler(actorScheduler);

    // internal transport is used for topology request
    final ClientTransportBuilder internalTransportBuilder =
        Transports.newClientTransport("broker-client-internal")
            .messageMaxLength(1024 * 1024)
            .messageMemoryPool(new UnboundedMemoryPool())
            .requestMemoryPool(new UnboundedMemoryPool())
            .scheduler(actorScheduler);

    transport = transportBuilder.build();
    internalTransport = internalTransportBuilder.build();

    topologyManager =
        new BrokerTopologyManagerImpl(internalTransport.getOutput(), this::registerEndpoint);
    actorScheduler.submitActor(topologyManager);

    requestManager =
        new BrokerRequestManager(
            transport.getOutput(),
            topologyManager,
            new RoundRobinDispatchStrategy(topologyManager),
            configuration.getCluster().getRequestTimeout());
    actorScheduler.submitActor(requestManager);

    final SocketAddress contactPoint =
        SocketAddress.from(configuration.getCluster().getContactPoint());
    registerEndpoint(ClientTransport.UNKNOWN_NODE_ID, contactPoint);
  }

  private void registerEndpoint(final int nodeId, final SocketAddress socketAddress) {
    registerEndpoint(transport, nodeId, socketAddress);
    registerEndpoint(internalTransport, nodeId, socketAddress);
  }

  private void registerEndpoint(
      final ClientTransport transport, final int nodeId, final SocketAddress socketAddress) {
    final RemoteAddress endpoint = transport.getEndpoint(nodeId);
    if (endpoint == null || !socketAddress.equals(endpoint.getAddress())) {
      transport.registerEndpoint(nodeId, socketAddress);
    }
  }

  @Override
  public void close() {
    if (isClosed) {
      return;
    }

    isClosed = true;

    LOG.debug("Closing client ...");

    doAndLogException(() -> topologyManager.close().join());
    LOG.debug("topology manager closed");
    doAndLogException(transport::close);
    LOG.debug("transport closed");
    doAndLogException(internalTransport::close);
    LOG.debug("internal transport closed");
    doAndLogException(dataFrameReceiveBuffer::close);
    LOG.debug("data frame receive buffer closed");

    try {
      actorScheduler.stop().get(15, TimeUnit.SECONDS);

      LOG.debug("Client closed.");
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException("Failed to gracefully shutdown client", e);
    }
  }

  protected void doAndLogException(final Runnable r) {
    try {
      r.run();
    } catch (final Exception e) {
      LOG.error("Exception when closing client. Ignoring", e);
    }
  }

  /* (non-Javadoc)
   * @see io.zeebe.gateway.impl.broker.BrokerClient#sendRequest(io.zeebe.gateway.impl.broker.request.BrokerRequest)
   */
  @Override
  public <T> ActorFuture<BrokerResponse<T>> sendRequest(BrokerRequest<T> request) {
    return requestManager.sendRequest(request);
  }

  @Override
  public <T> void sendRequest(
      BrokerRequest<T> request,
      BrokerResponseConsumer<T> responseConsumer,
      Consumer<Throwable> throwableConsumer) {
    requestManager.sendRequest(request, responseConsumer, throwableConsumer);
  }

  @Override
  public BrokerTopologyManager getTopologyManager() {
    return topologyManager;
  }

  public ClientTransport getTransport() {
    return transport;
  }

  public ActorScheduler getScheduler() {
    return actorScheduler;
  }
}
