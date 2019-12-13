/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.brokerapi;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;

import io.atomix.cluster.AtomixCluster;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.broker.protocol.brokerapi.data.Topology;
import io.zeebe.test.util.socket.SocketUtil;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.AtomixClientOutputAdapter;
import io.zeebe.transport.impl.AtomixRequestSubscription;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;

public final class StubBrokerRule extends ExternalResource {
  public static final int TEST_PARTITION_ID = DEPLOYMENT_PARTITION;
  protected final int nodeId;
  protected final SocketAddress socketAddress;
  protected ActorScheduler scheduler;
  protected MsgPackHelper msgPackHelper;
  protected final AtomicReference<Topology> currentTopology = new AtomicReference<>();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final int partitionCount;
  private StubResponseChannelHandler channelHandler;
  private final AtomicInteger requestCount = new AtomicInteger(0);
  private AtomixCluster cluster;
  private int currentStubPort;
  private String currentStubHost;

  public StubBrokerRule() {
    this.nodeId = 0;
    this.socketAddress = new SocketAddress(SocketUtil.getNextAddress());
    this.partitionCount = 1;
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
    final var communicationService = cluster.getCommunicationService();
    final var atomixClientOuputAdapter = new AtomixClientOutputAdapter(communicationService);
    final var atomixRequestSubscription = new AtomixRequestSubscription(communicationService);
    scheduler.submitActor(atomixClientOuputAdapter);

    channelHandler = new StubResponseChannelHandler(msgPackHelper);
    atomixRequestSubscription.subscribe(
        1,
        (bytes) -> {
          final var completableFuture = new CompletableFuture<byte[]>();
          final var requestId = requestCount.getAndIncrement();

          channelHandler.onRequest(
              response -> {
                final var length = response.getLength();
                final var bytes1 = new byte[length];
                final var unsafeBuffer = new UnsafeBuffer(bytes1);
                response.write(unsafeBuffer, 0);
                completableFuture.complete(bytes1);
                return true;
              },
              new UnsafeBuffer(bytes),
              0,
              bytes.length,
              requestId);

          return completableFuture;
        });

    currentTopology.set(topology);
  }

  public int getCurrentStubPort() {
    return currentStubPort;
  }

  public String getCurrentStubHost() {
    return currentStubHost;
  }

  @Override
  protected void after() {
    if (scheduler != null) {
      scheduler.stop();
    }
    cluster.stop().join();
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
