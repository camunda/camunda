/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerClusterStateImpl;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotTransferServiceClient;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransfer;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferServiceImpl;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.impl.AtomixClientTransportAdapter;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.util.NetUtil;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

public class SnapshotApiRequestHandlerTest {

  @RegisterExtension
  public final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @AutoClose MeterRegistry registry = new SimpleMeterRegistry();
  @TempDir Path temporaryFolder;
  final int partitionId = 1;
  ConstructableSnapshotStore senderSnapshotStore;
  ReceivableSnapshotStore receiverSnapshotStore;
  private AtomixClientTransportAdapter clientTransport;
  private String serverAddress;
  private AtomixServerTransport serverTransport;
  private SnapshotApiRequestHandler snapshotHandler;
  private SnapshotTransferServiceClient client;
  private BrokerClientImpl brokerClient;

  @BeforeEach
  void setup() {
    final var address = SocketUtil.getNextAddress();

    serverAddress = NetUtil.toSocketAddressString(address);
    final var messagingService =
        new NettyMessagingService(
            getClass().getSimpleName() + "-server",
            Address.from(serverAddress),
            new MessagingConfig(),
            registry);
    messagingService.start();
    scheduler.workUntilDone();

    final var clusterService = mock(ClusterEventService.class);
    final var brokerTopology = mock(BrokerTopologyManager.class);
    final var clusterState = new BrokerClusterStateImpl();
    clusterState.addBrokerIfAbsent(1);
    clusterState.addPartitionIfAbsent(1);
    clusterState.setPartitionLeader(1, 1, 1);
    clusterState.setBrokerAddressIfPresent(1, serverAddress);
    when(brokerTopology.getTopology()).thenReturn(clusterState);
    final var metrics = mock(BrokerClientRequestMetrics.class);
    brokerClient =
        new BrokerClientImpl(
            Duration.ofSeconds(5),
            messagingService,
            clusterService,
            scheduler.getActorScheduler(),
            brokerTopology,
            metrics);
    brokerClient.start();

    serverTransport = new AtomixServerTransport(messagingService, new SnowflakeIdGenerator(1L));
    scheduler.submitActor(serverTransport);
    scheduler.workUntilDone();

    snapshotHandler = new SnapshotApiRequestHandler(serverTransport);
    scheduler.submitActor(snapshotHandler);
    scheduler.workUntilDone();

    // Snapshot actors:
    final var senderDirectory = temporaryFolder.resolve("sender");
    final var receiverDirectory = temporaryFolder.resolve("receiver");
    senderSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, senderDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    scheduler.submitActor((Actor) senderSnapshotStore);
    scheduler.workUntilDone();

    final var transferService = new SnapshotTransferServiceImpl(senderSnapshotStore, 1);
    snapshotHandler.addTransferService(1, transferService);

    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, receiverDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());

    scheduler.submitActor((Actor) receiverSnapshotStore);
    scheduler.workUntilDone();
    client = new SnapshotTransferServiceClient(brokerClient);

    scheduler.workUntilDone();
  }

  @Test
  void shouldSendAllChunksCorrectly() {

    final var takeFuture = takePersistedSnapshot();
    scheduler.workUntilDone();
    assertThat(takeFuture).succeedsWithin(Duration.ofSeconds(30));

    final var transfer =
        new SnapshotTransfer(client, receiverSnapshotStore, (Actor) senderSnapshotStore);
    // when
    final var persistedSnapshot = transfer.getLatestSnapshot(partitionId);
    scheduler.workUntilDone();
    // then
    assertThat(persistedSnapshot)
        .succeedsWithin(Duration.ofSeconds(30))
        .satisfies(
            snapshot -> {
              final var lastSnapshotId = senderSnapshotStore.getLatestSnapshot().get().getId();
              final var snapshotId = snapshot.getId();
              assertThat(snapshotId).isEqualTo(lastSnapshotId);
            });
  }

  private ActorFuture<PersistedSnapshot> takePersistedSnapshot() {
    return SnapshotTransferUtil.takePersistedSnapshot(
        senderSnapshotStore,
        SnapshotTransferUtil.SNAPSHOT_FILE_CONTENTS,
        (Actor) receiverSnapshotStore);
  }
}
