/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.brokerapi;

import static io.camunda.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.Loggers;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import io.camunda.zeebe.test.broker.protocol.brokerapi.data.Topology;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.rules.ExternalResource;

public final class StubBrokerRule extends ExternalResource {
  private static final int TEST_PARTITION_ID = DEPLOYMENT_PARTITION;
  private final int nodeId;
  private final InetSocketAddress socketAddress;
  private ActorScheduler scheduler;
  private MsgPackHelper msgPackHelper;
  private final AtomicReference<Topology> currentTopology = new AtomicReference<>();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final int partitionCount;
  private StubRequestHandler channelHandler;
  private AtomixCluster cluster;
  private int currentStubPort;
  private String currentStubHost;
  private ServerTransport serverTransport;

  public StubBrokerRule() {
    nodeId = 0;
    socketAddress = SocketUtil.getNextAddress();
    partitionCount = 1;
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

    final Topology topology = new Topology();
    topology.addLeader(nodeId, socketAddress, Protocol.DEPLOYMENT_PARTITION);

    for (int i = TEST_PARTITION_ID; i < TEST_PARTITION_ID + partitionCount; i++) {
      topology.addLeader(nodeId, socketAddress, i);
    }

    final InetSocketAddress nextAddress = SocketUtil.getNextAddress();
    currentStubHost = nextAddress.getHostName();
    currentStubPort = nextAddress.getPort();
    cluster =
        AtomixCluster.builder()
            .withPort(currentStubPort)
            .withMemberId("0")
            .withClusterId("cluster")
            .build();
    cluster.start().join();
    final var transportFactory = new TransportFactory(scheduler);
    serverTransport = transportFactory.createServerTransport(0, cluster.getMessagingService());

    channelHandler = new StubRequestHandler(msgPackHelper);
    serverTransport.subscribe(1, RequestType.COMMAND, channelHandler);

    currentTopology.set(topology);
  }

  @Override
  protected void after() {
    try {
      serverTransport.close();
    } catch (final Exception e) {
      Loggers.PROTOCOL_LOGGER.error("Error on closing server transport.", e);
    }
    if (scheduler != null) {
      scheduler.stop();
    }
    cluster.stop().join();
  }

  public int getCurrentStubPort() {
    return currentStubPort;
  }

  public String getCurrentStubHost() {
    return currentStubHost;
  }

  private ExecuteCommandResponseTypeBuilder onExecuteCommandRequest(
      final Predicate<ExecuteCommandRequest> activationFunction) {
    return new ExecuteCommandResponseTypeBuilder(
        channelHandler::addExecuteCommandRequestStub, activationFunction, msgPackHelper);
  }

  public ExecuteCommandResponseTypeBuilder onExecuteCommandRequest(
      final ValueType eventType, final Intent intent) {
    return onExecuteCommandRequest(ecr -> ecr.valueType() == eventType && ecr.intent() == intent);
  }

  public List<ExecuteCommandRequest> getReceivedCommandRequests() {
    return channelHandler.getReceivedCommandRequests();
  }

  public JobStubs jobs() {
    return new JobStubs(this);
  }

  public InetSocketAddress getSocketAddress() {
    return socketAddress;
  }

  public int getNodeId() {
    return nodeId;
  }

  public ControlledActorClock getClock() {
    return clock;
  }
}
