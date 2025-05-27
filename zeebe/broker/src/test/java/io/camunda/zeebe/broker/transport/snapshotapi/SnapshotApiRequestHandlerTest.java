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
import io.camunda.zeebe.scheduler.ActorScheduler;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SnapshotApiRequestHandlerTest {
  @AutoClose
  private static final ActorScheduler scheduler =
      new ActorScheduler.ActorSchedulerBuilder()
          .setSchedulerName("SnapshotApiRequestHandlerTest")
          .build();

  @AutoClose MeterRegistry registry = new SimpleMeterRegistry();
  @TempDir Path temporaryFolder;
  final int partitionId = 1;
  @AutoClose ConstructableSnapshotStore senderSnapshotStore;
  @AutoClose ReceivableSnapshotStore receiverSnapshotStore;
  @AutoClose private AtomixClientTransportAdapter clientTransport;
  private String serverAddress;
  @AutoClose private AtomixServerTransport serverTransport;
  @AutoClose private SnapshotApiRequestHandler snapshotHandler;
  private SnapshotTransferServiceClient client;
  @AutoClose private BrokerClientImpl brokerClient;

  @BeforeAll
  public static void beforeAll() {
    scheduler.start();
  }

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
    messagingService.start().join();

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
            scheduler,
            brokerTopology,
            metrics);
    brokerClient.start();

    serverTransport = new AtomixServerTransport(messagingService, new SnowflakeIdGenerator(1L));
    scheduler.submitActor(serverTransport).join();

    snapshotHandler = new SnapshotApiRequestHandler(serverTransport);
    scheduler.submitActor(snapshotHandler).join();

    // Snapshot actors:
    final var senderDirectory = temporaryFolder.resolve("sender");
    final var receiverDirectory = temporaryFolder.resolve("receiver");
    senderSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, senderDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());
    scheduler.submitActor((Actor) senderSnapshotStore).join();

    snapshotHandler.addTransferService(1, new SnapshotTransferServiceImpl(senderSnapshotStore, 1));

    receiverSnapshotStore =
        new FileBasedSnapshotStore(
            0, partitionId, receiverDirectory, snapshotPath -> Map.of(), new SimpleMeterRegistry());

    scheduler.submitActor((Actor) receiverSnapshotStore).join();
    client = new SnapshotTransferServiceClient(brokerClient);
  }

  //  @AfterEach
  //  void shutdown() {
  //    // Cannot use @Autoclose annotation because the order of closing is not correct.
  //    CloseHelper.closeAll(
  //        senderSnapshotStore,
  //        receiverSnapshotStore,
  //        brokerClient,
  //        serverTransport,
  //        clientTransport,
  //        snapshotHandler,
  //        scheduler);
  //  }

  @Test
  void shouldSendAllChunksCorrectly() {

    takePersistedSnapshot();
    final var transfer =
        new SnapshotTransfer(client, receiverSnapshotStore, (Actor) senderSnapshotStore);
    // when
    final var persistedSnapshot = transfer.getLatestSnapshot(partitionId).join();
    // then
    assertThat(persistedSnapshot.getId())
        .isEqualTo(senderSnapshotStore.getLatestSnapshot().get().getId());
  }

  private PersistedSnapshot takePersistedSnapshot() {
    return SnapshotTransferUtil.takePersistedSnapshot(
        senderSnapshotStore, SnapshotTransferUtil.SNAPSHOT_FILE_CONTENTS);
  }
}
