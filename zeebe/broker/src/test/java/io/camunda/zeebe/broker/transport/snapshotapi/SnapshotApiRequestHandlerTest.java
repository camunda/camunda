/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.BrokerMemberId;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
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
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
  final PartitionId partition =
      new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionId);
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
    when(clusterState.getLeaderForPartition(1)).thenReturn(BrokerMemberId.from(1));
    when(clusterState.getBrokerAddress(BrokerMemberId.from("1"))).thenReturn(serverAddress);
    when(clusterState.getPartitions()).thenReturn(List.of(1));

    when(brokerTopology.getTopology()).thenReturn(clusterState);
    when(brokerTopology.getTopology(anyString())).thenReturn(clusterState);
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
        submitActor(
            new AtomixServerTransport(messagingService, new SnowflakeIdGenerator(1L), true));

    scaleUpProgressInvocationCount = new AtomicInteger();
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
                snapshotPath -> SnapshotFilesInfo.none(),
                new SimpleMeterRegistry()));

    snapshotHandler = submitActor(newHandler(partition, senderSnapshotStore));

    receiverSnapshotStore =
        submitActor(
            new FileBasedSnapshotStore(
                0,
                partitionId,
                receiverDirectory,
                snapshotPath -> SnapshotFilesInfo.none(),
                new SimpleMeterRegistry()));

    client =
        new SnapshotTransferServiceClient(
            brokerClient, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);

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
    mockBootstrappedAtWith(partition, position);

    final var transfer =
        submitActor(
            new SnapshotTransferImpl(
                new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1),
                ignore -> client,
                snapshotMetrics,
                receiverSnapshotStore));
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

  // Regression test for https://github.com/camunda/camunda/issues/60676: partitions of different
  // partition groups (physical tenants) share partition numbers, so the handler serving one group's
  // partition must never answer requests addressed to another group's partition of the same number.
  @Test
  void shouldServeSnapshotsFromTheRequestedPartitionGroup() {
    // given -- a second physical tenant whose partition has the same number as the default one
    final var tenantPartition = new PartitionId("tenanta", partitionId);
    final var tenantSenderStore =
        submitActor(
            new FileBasedSnapshotStore(
                0,
                partitionId,
                temporaryFolder.resolve("tenant-sender"),
                snapshotPath -> SnapshotFilesInfo.none(),
                new SimpleMeterRegistry()));
    submitActor(newHandler(tenantPartition, tenantSenderStore));

    final var defaultSnapshot = takePersistedSnapshot(11L);
    final var tenantSnapshot =
        SnapshotTransferUtil.takePersistedSnapshot(
            tenantSenderStore,
            SnapshotTransferUtil.SNAPSHOT_FILE_CONTENTS,
            57L,
            receiverSnapshotStore);
    scheduler.workUntilDone();
    assertThat(defaultSnapshot).succeedsWithin(Duration.ofSeconds(30));
    assertThat(tenantSnapshot).succeedsWithin(Duration.ofSeconds(30));
    mockBootstrappedAtWith(tenantPartition, 57L);

    final var transfer =
        submitActor(
            new SnapshotTransferImpl(
                tenantPartition,
                ignore -> new SnapshotTransferServiceClient(brokerClient, "tenanta"),
                snapshotMetrics,
                receiverSnapshotStore));

    // when
    final var persistedSnapshot = transfer.getLatestSnapshot(partitionId);
    scheduler.workUntilDone();

    // then -- the received snapshot is the tenant's own, not the default tenant's
    assertThat(persistedSnapshot)
        .succeedsWithin(Duration.ofSeconds(30))
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getId())
                  .isEqualTo(tenantSenderStore.getLatestSnapshot().get().getId());
              assertThat(snapshot.getId())
                  .isNotEqualTo(senderSnapshotStore.getLatestSnapshot().get().getId());
            });
  }

  private SnapshotApiRequestHandler newHandler(
      final PartitionId partition, final FileBasedSnapshotStore snapshotStore) {
    return new SnapshotApiRequestHandler(
        partition,
        serverTransport,
        brokerClient,
        concurrency ->
            new SnapshotTransferServiceImpl(
                snapshotStore,
                takeSnapshotMock,
                partition.number(),
                SnapshotCopyUtil::copyAllFiles,
                concurrency));
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

  private void mockBootstrappedAtWith(final PartitionId partition, final long position) {
    serverTransport.subscribe(
        partition,
        RequestType.COMMAND,
        (output, partitionNumber, requestId, buffer, offset, length) -> {
          // assume the request is a GetScaleUpProgress
          scaleUpProgressInvocationCount.incrementAndGet();
          final var writer =
              new CommandResponseWriterImpl(output)
                  .partitionId(partitionNumber)
                  .valueWriter(new ScaleRecord().statusResponse(3, List.of(), 2, position))
                  .recordType(RecordType.COMMAND)
                  .valueType(ValueType.SCALE);
          output.sendResponse(
              new ServerResponseImpl()
                  .writer(writer)
                  .setPartitionId(partitionNumber)
                  .setRequestId(requestId));
        });
  }
}
