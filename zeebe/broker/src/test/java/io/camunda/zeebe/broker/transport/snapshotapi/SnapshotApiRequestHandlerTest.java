/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotTransferServiceClient;
import io.camunda.zeebe.broker.transport.commandapi.CommandResponseWriterImpl;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.SnapshotCopyUtil;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.impl.SnapshotMetrics;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferImpl;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferService.TakeSnapshot;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferServiceImpl;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixClientTransportAdapter;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.transport.impl.ServerResponseImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.util.NetUtil;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SnapshotApiRequestHandlerTest {

  @RegisterExtension
  public final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @AutoClose MeterRegistry registry = new SimpleMeterRegistry();
  @TempDir Path temporaryFolder;
  final int partitionId = 1;
  FileBasedSnapshotStore senderSnapshotStore;
  FileBasedSnapshotStore receiverSnapshotStore;
  private AtomixClientTransportAdapter clientTransport;
  private String serverAddress;
  private AtomixServerTransport serverTransport;
  private SnapshotApiRequestHandler snapshotHandler;
  private SnapshotTransferServiceClient client;
  private BrokerClientImpl brokerClient;
  private TakeSnapshot takeSnapshotMock;
  private AtomicInteger scaleUpProgressInvocationCount;
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final SnapshotMetrics snapshotMetrics = new SnapshotMetrics(meterRegistry);

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
    final var clusterState = mock(BrokerClusterState.class);
    when(clusterState.getLeaderForPartition(1)).thenReturn(1);
    when(clusterState.getBrokerAddress(1)).thenReturn(serverAddress);
    when(clusterState.getPartitions()).thenReturn(List.of(1));

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

    serverTransport =
        submitActor(new AtomixServerTransport(messagingService, new SnowflakeIdGenerator(1L)));

    scaleUpProgressInvocationCount = new AtomicInteger();

    snapshotHandler = submitActor(new SnapshotApiRequestHandler(serverTransport, brokerClient));

    takeSnapshotMock = mock(TakeSnapshot.class);
    // Snapshot actors:
    final var senderDirectory = temporaryFolder.resolve("sender");
    final var receiverDirectory = temporaryFolder.resolve("receiver");
    senderSnapshotStore =
        submitActor(
            new FileBasedSnapshotStore(
                0,
                partitionId,
                senderDirectory,
                snapshotPath -> Map.of(),
                new SimpleMeterRegistry()));

    final var transferService =
        new SnapshotTransferServiceImpl(
            senderSnapshotStore,
            takeSnapshotMock,
            1,
            SnapshotCopyUtil::copyAllFiles,
            snapshotHandler);
    snapshotHandler.addTransferService(1, transferService);

    receiverSnapshotStore =
        submitActor(
            new FileBasedSnapshotStore(
                0,
                partitionId,
                receiverDirectory,
                snapshotPath -> Map.of(),
                new SimpleMeterRegistry()));

    client = new SnapshotTransferServiceClient(brokerClient);

    scheduler.workUntilDone();
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 11L, 100L})
  // last processed position in the snapshot.
  // required position is always scalingStartedAt
  void shouldSendAllChunksCorrectly(final long position) {
    // given
    final var snapshotProcessedPosition = 11L;
    final var takeFuture = takePersistedSnapshot(snapshotProcessedPosition);
    scheduler.workUntilDone();
    assertThat(takeFuture).succeedsWithin(Duration.ofSeconds(30));
    if (position > snapshotProcessedPosition) {
      when(takeSnapshotMock.takeSnapshot(eq(position))).thenReturn(takePersistedSnapshot(position));
    }
    mockBootstrappedAtWith(position);

    final var transfer =
        submitActor(
            new SnapshotTransferImpl(ignore -> client, snapshotMetrics, receiverSnapshotStore));
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

    assertThat(snapshotMetrics.getTransferDuration(true).mean(TimeUnit.MILLISECONDS))
        .isGreaterThan(0.1D);
  }

  private ActorFuture<PersistedSnapshot> takePersistedSnapshot(final long processedPosition) {
    return SnapshotTransferUtil.takePersistedSnapshot(
        senderSnapshotStore,
        SnapshotTransferUtil.SNAPSHOT_FILE_CONTENTS,
        processedPosition,
        receiverSnapshotStore);
  }

  private <A extends Actor> A submitActor(final A actor) {
    final var future = scheduler.submitActor(actor);
    scheduler.workUntilDone();
    assertThat(future).succeedsWithin(Duration.ofSeconds(30));
    return actor;
  }

  private void mockBootstrappedAtWith(final long position) {
    serverTransport.subscribe(
        1,
        RequestType.COMMAND,
        (output, partition, requestId, buffer, offset, length) -> {
          // assume the request is a GetScaleUpProgress
          scaleUpProgressInvocationCount.incrementAndGet();
          final var writer =
              new CommandResponseWriterImpl(output)
                  .partitionId(partitionId)
                  .valueWriter(new ScaleRecord().statusResponse(3, List.of(), 2, position))
                  .recordType(RecordType.COMMAND)
                  .valueType(ValueType.SCALE);
          output.sendResponse(
              new ServerResponseImpl()
                  .writer(writer)
                  .setPartitionId(partitionId)
                  .setRequestId(requestId));
        });
  }
}
