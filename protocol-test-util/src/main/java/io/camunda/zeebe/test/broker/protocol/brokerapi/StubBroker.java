/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol.brokerapi;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Member;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.protocol.impl.Loggers;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.test.broker.protocol.MsgPackHelper;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.transport.TransportFactory;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Predicate;

public final class StubBroker implements AutoCloseable {

  private static final String CLUSTER_ID = "cluster";
  private final int nodeId;
  private final InetSocketAddress socketAddress;
  private ActorScheduler scheduler;
  private MsgPackHelper msgPackHelper;
  private final ControlledActorClock clock = new ControlledActorClock();
  private StubRequestHandler channelHandler;
  private AtomixCluster cluster;
  private int currentStubPort;
  private String currentStubHost;
  private ServerTransport serverTransport;

  public StubBroker() {
    nodeId = 0;
    socketAddress = SocketUtil.getNextAddress();
  }

  public StubBroker start() {
    msgPackHelper = new MsgPackHelper();

    final int numThreads = 2;
    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(numThreads)
            .setActorClock(clock)
            .build();

    scheduler.start();

    currentStubHost = socketAddress.getHostName();
    currentStubPort = socketAddress.getPort();
    cluster =
        AtomixCluster.builder()
            .withPort(currentStubPort)
            .withMemberId("0")
            .withClusterId(CLUSTER_ID)
            .build();
    cluster.start().join();

    final var transportFactory = new TransportFactory(scheduler);
    serverTransport = transportFactory.createServerTransport(nodeId, cluster.getMessagingService());

    channelHandler = new StubRequestHandler(msgPackHelper);
    serverTransport.subscribe(1, RequestType.COMMAND, channelHandler);

    writeBrokerInfoProperties();
    return this;
  }

  public Member member() {
    return cluster.getMembershipService().getLocalMember();
  }

  private void writeBrokerInfoProperties() {
    final var brokerInfo =
        new BrokerInfo()
            .setCommandApiAddress(Address.from("localhost", socketAddress.getPort()).toString())
            .setClusterSize(1)
            .setReplicationFactor(1)
            .setPartitionsCount(1)
            .setNodeId(0)
            .setPartitionHealthy(1)
            .setLeaderForPartition(1, 1);
    brokerInfo.writeIntoProperties(cluster.getMembershipService().getLocalMember().properties());
  }

  @Override
  public void close() {
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

  public int getNodeId() {
    return nodeId;
  }

  public String clusterId() {
    return CLUSTER_ID;
  }
}
