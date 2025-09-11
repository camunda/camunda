/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.storage.log.RaftLogReader;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.management.PartitionStatus;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.snapshots.SnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class BrokerSnapshotTest {

  private static final int PARTITION_ID = 1;
  @Rule public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  private RaftLogReader journalReader;
  private BrokerAdminService brokerAdminService;
  private CamundaClient client;

  @Before
  public void setup() {
    final RaftPartition raftPartition =
        brokerRule
            .getBroker()
            .getBrokerContext()
            .getPartitionManager()
            .getRaftPartition(PARTITION_ID);
    journalReader = raftPartition.getServer().openReader();
    brokerAdminService = brokerRule.getBroker().getBrokerContext().getBrokerAdminService();

    final CamundaClientBuilder camundaClientBuilder =
        CamundaClient.newClientBuilder()
            .grpcAddress(brokerRule.getGrpcAddress())
            .preferRestOverGrpc(false);
    client = camundaClientBuilder.build();
  }

  @After
  public void after() {
    CloseHelper.closeAll(client, journalReader);
  }

  @Test
  public void shouldTakeSnapshotAtCorrectIndex() {
    // given
    createSomeEvents();

    // when
    brokerAdminService.takeSnapshot();
    final SnapshotId snapshotId = waitForSnapshotAtBroker(brokerAdminService, PARTITION_ID);

    // then
    final long processedIndex = journalReader.seekToAsqn(snapshotId.getProcessedPosition());
    final long expectedSnapshotIndex = processedIndex - 1;

    assertThat(snapshotId.getIndex()).isEqualTo(expectedSnapshotIndex);
  }

  private void createSomeEvents() {
    final var lastMessageKey =
        IntStream.range(0, 10).mapToLong(this::publishMaxMessageSizeMessage).max().getAsLong();

    // wait until all records are exported, to ensure the snapshot position is always the same.
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                            .withRecordKey(lastMessageKey)
                            .exists())
                    .describedAs("All records are exported")
                    .isTrue());
  }

  private long publishMaxMessageSizeMessage(final int key) {
    return client
        .newPublishMessageCommand()
        .messageName("msg")
        .correlationKey("msg-" + key)
        .send()
        .join()
        .getMessageKey();
  }

  private SnapshotId waitForSnapshotAtBroker(
      final BrokerAdminService adminService, final int partitionId) {
    Awaitility.await()
        .pollInterval(1, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        adminService
                            .getPartitionStatus()
                            .get(partitionId)
                            .processedPositionInSnapshot())
                    .isNotNull());
    final PartitionStatus partitionStatus = brokerAdminService.getPartitionStatus().get(1);
    return FileBasedSnapshotId.ofFileName(partitionStatus.snapshotId()).getOrThrow();
  }
}
