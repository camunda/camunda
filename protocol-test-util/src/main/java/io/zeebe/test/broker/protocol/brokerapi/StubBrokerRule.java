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
package io.zeebe.test.broker.protocol.brokerapi;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.broker.protocol.brokerapi.data.Topology;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

public class StubBrokerRule extends ExternalResource {
  public static final int TEST_PARTITION_ID = DEPLOYMENT_PARTITION;

  private final ControlledActorClock clock = new ControlledActorClock();
  protected ActorScheduler scheduler;

  protected final int nodeId;
  protected final SocketAddress socketAddress;
  private final int clusterSize;
  private final int partitionCount;
  private final int replicationFactor;

  protected ServerTransport transport;

  protected StubResponseChannelHandler channelHandler;
  protected MsgPackHelper msgPackHelper;

  protected AtomicReference<Topology> currentTopology = new AtomicReference<>();

  public StubBrokerRule() {
    this(0);
  }

  public StubBrokerRule(final int nodeId) {
    this(nodeId, 1, 1, 1);
  }

  private StubBrokerRule(
      final int nodeId,
      final int clusterSize,
      final int partitionCount,
      final int replicationFactor) {
    this.nodeId = nodeId;
    this.socketAddress = SocketUtil.getNextAddress();
    this.clusterSize = clusterSize;
    this.partitionCount = partitionCount;
    this.replicationFactor = replicationFactor;
  }

  @Override
  protected void before() {
    msgPackHelper = new MsgPackHelper();

    final int numThreads = 2;
    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(numThreads)
            .setActorClock(clock)
            .build();

    scheduler.start();

    channelHandler = new StubResponseChannelHandler(msgPackHelper);

    final Topology topology = new Topology();
    topology.addLeader(nodeId, socketAddress, Protocol.DEPLOYMENT_PARTITION);

    for (int i = TEST_PARTITION_ID; i < TEST_PARTITION_ID + partitionCount; i++) {
      topology.addLeader(nodeId, socketAddress, i);
    }

    currentTopology.set(topology);
    stubTopologyRequest();
    bindTransport();
  }

  @Override
  protected void after() {
    if (transport != null) {
      closeTransport();
    }
    if (scheduler != null) {
      scheduler.stop();
    }
  }

  public void interruptAllServerChannels() {
    transport.interruptAllChannels();
  }

  public void closeTransport() {
    if (transport != null) {
      transport.close();
      transport = null;
    } else {
      throw new RuntimeException("transport not open");
    }
  }

  public void bindTransport() {
    if (transport == null) {
      transport =
          Transports.newServerTransport()
              .bindAddress(socketAddress.toInetSocketAddress())
              .scheduler(scheduler)
              .build(null, channelHandler);
    } else {
      throw new RuntimeException("transport already open");
    }
  }

  public ServerTransport getTransport() {
    return transport;
  }

  public ExecuteCommandResponseTypeBuilder onExecuteCommandRequest() {
    return onExecuteCommandRequest((r) -> true);
  }

  public ExecuteCommandResponseTypeBuilder onExecuteCommandRequest(
      final Predicate<ExecuteCommandRequest> activationFunction) {
    return new ExecuteCommandResponseTypeBuilder(
        channelHandler::addExecuteCommandRequestStub, activationFunction, msgPackHelper);
  }

  public ExecuteCommandResponseTypeBuilder onExecuteCommandRequest(
      final ValueType eventType, final Intent intent) {
    return onExecuteCommandRequest(ecr -> ecr.valueType() == eventType && ecr.intent() == intent);
  }

  public ExecuteCommandResponseTypeBuilder onExecuteCommandRequest(
      final int partitionId, final ValueType valueType, final Intent intent) {
    return onExecuteCommandRequest(
        ecr ->
            ecr.partitionId() == partitionId
                && ecr.valueType() == valueType
                && ecr.intent() == intent);
  }

  public ControlMessageResponseTypeBuilder onControlMessageRequest() {
    return onControlMessageRequest((r) -> true);
  }

  public ControlMessageResponseTypeBuilder onControlMessageRequest(
      final Predicate<ControlMessageRequest> activationFunction) {
    return new ControlMessageResponseTypeBuilder(
        channelHandler::addControlMessageRequestStub, activationFunction, msgPackHelper);
  }

  public List<ControlMessageRequest> getReceivedControlMessageRequests() {
    return channelHandler.getReceivedControlMessageRequests();
  }

  public List<ControlMessageRequest> getReceivedControlMessageRequestsByType(
      final ControlMessageType type) {
    return channelHandler
        .getReceivedControlMessageRequests()
        .stream()
        .filter((r) -> type == r.messageType())
        .collect(Collectors.toList());
  }

  public List<ExecuteCommandRequest> getReceivedCommandRequests() {
    return channelHandler.getReceivedCommandRequests();
  }

  public List<Object> getAllReceivedRequests() {
    return channelHandler.getAllReceivedRequests();
  }

  public void stubTopologyRequest() {
    onTopologyRequest()
        .respondWith()
        .data()
        .put("brokers", r -> currentTopology.get().getBrokers())
        .put("clusterSize", clusterSize)
        .put("partitionsCount", partitionCount)
        .put("replicationFactor", replicationFactor)
        .done()
        .register();
  }

  public ControlMessageResponseTypeBuilder onTopologyRequest() {
    return onControlMessageRequest(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY);
  }

  public void addPartition(final int partition) {
    final Topology newTopology = new Topology(currentTopology.get());

    newTopology.addLeader(nodeId, socketAddress, partition);
    currentTopology.set(newTopology);
  }

  public void setCurrentTopology(final Topology currentTopology) {
    this.currentTopology.set(currentTopology);
  }

  public void clearTopology() {
    currentTopology.set(new Topology());
  }

  public JobStubs jobs() {
    return new JobStubs(this);
  }

  public SocketAddress getSocketAddress() {
    return socketAddress;
  }

  public int getNodeId() {
    return nodeId;
  }

  public ControlledActorClock getClock() {
    return clock;
  }
}
