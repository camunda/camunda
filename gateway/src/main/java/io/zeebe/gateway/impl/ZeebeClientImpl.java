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
package io.zeebe.gateway.impl;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.ZeebeClientConfiguration;
import io.zeebe.gateway.api.clients.TopicClient;
import io.zeebe.gateway.api.commands.TopicsRequestStep1;
import io.zeebe.gateway.api.commands.TopologyRequestStep1;
import io.zeebe.gateway.api.record.ZeebeObjectMapper;
import io.zeebe.gateway.api.subscription.ManagementSubscriptionBuilderStep1;
import io.zeebe.gateway.impl.clustering.ClientTopologyManager;
import io.zeebe.gateway.impl.clustering.TopologyRequestImpl;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.gateway.impl.subscription.SubscriptionManager;
import io.zeebe.gateway.impl.subscription.topic.ManagementSubscriptionBuilderImpl;
import io.zeebe.gateway.impl.topic.TopicsRequestImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ClientTransportBuilder;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.memory.BlockingMemoryPool;
import io.zeebe.transport.impl.memory.UnboundedMemoryPool;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;

public class ZeebeClientImpl implements ZeebeClient {
  public static final Logger LOG = Loggers.CLIENT_LOGGER;

  public static final String VERSION;

  static {
    final String version = ZeebeClient.class.getPackage().getImplementationVersion();
    VERSION = version != null ? version : "development";
  }

  protected final ZeebeClientConfiguration configuration;

  protected Dispatcher dataFrameReceiveBuffer;
  protected ActorScheduler scheduler;

  protected ClientTransport transport;

  protected final ZeebeObjectMapperImpl objectMapper;

  protected final ClientTopologyManager topologyManager;
  protected final RequestManager apiCommandManager;
  protected final SubscriptionManager subscriptionManager;
  protected final PartitionManager partitionManager;

  protected boolean isClosed;

  private ClientTransport internalTransport;

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration) {
    this(configuration, null);
  }

  public ZeebeClientImpl(final ZeebeClientConfiguration configuration, ActorClock actorClock) {
    LOG.info("Version: {}", VERSION);

    this.configuration = configuration;

    final SocketAddress contactPoint = SocketAddress.from(configuration.getBrokerContactPoint());

    this.scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(configuration.getNumManagementThreads())
            .setIoBoundActorThreadCount(0)
            .setActorClock(actorClock)
            .setSchedulerName("client")
            .build();
    this.scheduler.start();

    final ByteValue sendBufferSize = ByteValue.ofMegabytes(configuration.getSendBufferSize());
    final long requestBlockTimeMs = configuration.getRequestBlocktime().toMillis();

    dataFrameReceiveBuffer =
        Dispatchers.create("receive-buffer")
            .bufferSize(sendBufferSize)
            .modePubSub()
            .frameMaxLength(1024 * 1024)
            .actorScheduler(scheduler)
            .build();

    final ClientTransportBuilder transportBuilder =
        Transports.newClientTransport("broker")
            .messageMaxLength(1024 * 1024)
            .messageReceiveBuffer(dataFrameReceiveBuffer)
            .messageMemoryPool(
                new UnboundedMemoryPool()) // Client is not sending any heavy messages
            .requestMemoryPool(new BlockingMemoryPool(sendBufferSize, requestBlockTimeMs))
            .scheduler(scheduler);

    // internal transport is used for topology request
    final ClientTransportBuilder internalTransportBuilder =
        Transports.newClientTransport("broker-internal")
            .messageMaxLength(1024 * 1024)
            .messageMemoryPool(new UnboundedMemoryPool())
            .requestMemoryPool(new UnboundedMemoryPool())
            .scheduler(scheduler);

    if (configuration.getTcpChannelKeepAlivePeriod() != null) {
      transportBuilder.keepAlivePeriod(configuration.getTcpChannelKeepAlivePeriod());
      internalTransportBuilder.keepAlivePeriod(configuration.getTcpChannelKeepAlivePeriod());
    }

    transport = transportBuilder.build();
    internalTransport = internalTransportBuilder.build();

    this.objectMapper = new ZeebeObjectMapperImpl();

    final RemoteAddress initialContactPoint = transport.registerRemoteAddress(contactPoint);

    topologyManager =
        new ClientTopologyManager(transport, internalTransport, objectMapper, initialContactPoint);
    scheduler.submitActor(topologyManager);

    apiCommandManager =
        new RequestManager(
            transport.getOutput(),
            topologyManager,
            objectMapper,
            configuration.getRequestTimeout(),
            requestBlockTimeMs);
    this.scheduler.submitActor(apiCommandManager);

    this.subscriptionManager = new SubscriptionManager(this);
    this.transport.registerChannelListener(subscriptionManager);
    this.scheduler.submitActor(subscriptionManager);

    this.partitionManager = new PartitionManager(this);
  }

  @Override
  public void close() {
    if (isClosed) {
      return;
    }

    isClosed = true;

    LOG.debug("Closing client ...");

    doAndLogException(() -> subscriptionManager.close().join());
    LOG.debug("subscriber group manager closed");
    doAndLogException(() -> apiCommandManager.close().join());
    LOG.debug("api command manager closed");
    doAndLogException(() -> topologyManager.close().join());
    LOG.debug("topology manager closed");
    doAndLogException(() -> transport.close());
    LOG.debug("transport closed");
    doAndLogException(() -> internalTransport.close());
    LOG.debug("internal transport closed");
    doAndLogException(() -> dataFrameReceiveBuffer.close());
    LOG.debug("data frame receive buffer closed");

    try {
      scheduler.stop().get(15, TimeUnit.SECONDS);

      LOG.debug("Client closed.");
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException("Could not shutdown client successfully", e);
    }
  }

  protected void doAndLogException(Runnable r) {
    try {
      r.run();
    } catch (Exception e) {
      Loggers.CLIENT_LOGGER.error("Exception when closing client. Ignoring", e);
    }
  }

  public RequestManager getCommandManager() {
    return apiCommandManager;
  }

  public ClientTopologyManager getTopologyManager() {
    return topologyManager;
  }

  public ZeebeObjectMapperImpl getObjectMapper() {
    return objectMapper;
  }

  @Override
  public ZeebeClientConfiguration getConfiguration() {
    return configuration;
  }

  public ClientTransport getTransport() {
    return transport;
  }

  public ActorScheduler getScheduler() {
    return scheduler;
  }

  public SubscriptionManager getSubscriptionManager() {
    return subscriptionManager;
  }

  public PartitionManager getPartitionManager() {
    return partitionManager;
  }

  @Override
  public TopicClient topicClient() {
    return new TopicClientImpl(this, Protocol.DEFAULT_TOPIC);
  }

  @Override
  public ZeebeObjectMapper objectMapper() {
    return objectMapper;
  }

  @Override
  public TopicsRequestStep1 newTopicsRequest() {
    return new TopicsRequestImpl(getCommandManager());
  }

  @Override
  public TopologyRequestStep1 newTopologyRequest() {
    return new TopologyRequestImpl(getCommandManager(), topologyManager);
  }

  @Override
  public ManagementSubscriptionBuilderStep1 newManagementSubscription() {
    return new ManagementSubscriptionBuilderImpl(subscriptionManager, configuration);
  }
}
