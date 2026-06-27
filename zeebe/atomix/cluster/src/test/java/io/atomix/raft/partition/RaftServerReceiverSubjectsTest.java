/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.partition;

import static io.atomix.raft.partition.RaftPartition.PARTITION_NAME_FORMAT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RaftServerReceiverSubjectsTest {

  private static final String PARTITION_GROUP = "group";
  private static final String DEFAULT_GROUP = Protocol.DEFAULT_PARTITION_GROUP_NAME;
  private static final String LEGACY_GROUP = "raft-partition";
  private static final MemberId MEMBER_ID = new MemberId("0");
  private static final PartitionId PARTITION_ID = new PartitionId(PARTITION_GROUP, 1);

  @Mock private ClusterMembershipService clusterMembershipService;
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  @Mock private ClusterCommunicationService clusterCommunicationService;
  @Mock private ReceivableSnapshotStore receivableSnapshotStore;

  private RaftPartitionServer createRaftPartitionServer(
      final String group, final RaftPartitionConfig raftPartitionConfig, final Path tempDir) {
    final var metadata =
        new PartitionMetadata(
            new PartitionId(group, PARTITION_ID.number()), Set.of(), Map.of(), 1, MEMBER_ID);
    final var raftPartition =
        new RaftPartition(metadata, raftPartitionConfig, tempDir.toFile(), meterRegistry);
    return new RaftPartitionServer(
        raftPartition,
        raftPartitionConfig,
        MEMBER_ID,
        clusterMembershipService,
        clusterCommunicationService,
        receivableSnapshotStore,
        metadata,
        meterRegistry);
  }

  @ParameterizedTest
  @MethodSource("provideScenarios")
  void shouldRegisterSubjects(
      final String group,
      final boolean receiveOnLegacySubject,
      final Map<String, Integer> expectedHandlersByGroup,
      @TempDir final Path tempDir) {
    // given
    final var config = createConfig(receiveOnLegacySubject);

    // when
    createRaftPartitionServer(group, config, tempDir);

    // then
    expectedHandlersByGroup.forEach(this::assertSubjectsRegistered);
  }

  @ParameterizedTest
  @MethodSource("provideScenarios")
  void shouldUnregisterSubjects(
      final String group,
      final boolean receiveOnLegacySubject,
      final Map<String, Integer> expectedHandlersByGroup,
      @TempDir final Path tempDir) {
    // given
    final var config = createConfig(receiveOnLegacySubject);
    final var server = createRaftPartitionServer(group, config, tempDir);

    // when
    server.stop().join();

    // then
    expectedHandlersByGroup.forEach(this::assertSubjectsUnregistered);
  }

  void assertSubjectsRegistered(final String prefix, final int expected) {
    final var subjectPrefix =
        PARTITION_NAME_FORMAT.formatted(prefix, PARTITION_ID.number()) + "-%s";
    final var expectedNumberOfInvocations = expected > 0 ? times(expected) : never();

    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("append")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("append-versioned")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("configure")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("force-configure")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("install")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("join")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("leave")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("poll")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("reconfigure")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("vote")), any(), any(), any());
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .replyTo(eq(subjectPrefix.formatted("transfer")), any(), any(), any());
  }

  void assertSubjectsUnregistered(final String prefix, final int expected) {
    final var partitionName =
        PARTITION_NAME_FORMAT.formatted(prefix, PARTITION_ID.number()) + "-%s";
    final var expectedNumberOfInvocations = expected > 0 ? times(expected) : never();

    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("append")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("append-versioned")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("configure")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("force-configure")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("install")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("join")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("leave")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("poll")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("reconfigure")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("vote")));
    verify(clusterCommunicationService, expectedNumberOfInvocations)
        .unsubscribe(eq(partitionName.formatted("transfer")));
  }

  static Stream<Arguments> provideScenarios() {
    return Stream.of(
        // the default group receives on both the legacy and the default subject
        Arguments.of(DEFAULT_GROUP, true, Map.of(LEGACY_GROUP, 1, DEFAULT_GROUP, 1)),
        // with legacy receive disabled, the default group receives only on its own subject
        Arguments.of(DEFAULT_GROUP, false, Map.of(LEGACY_GROUP, 0, DEFAULT_GROUP, 1)),
        // a non-default group never receives on the legacy subject, regardless of the flag
        Arguments.of(PARTITION_GROUP, true, Map.of(LEGACY_GROUP, 0, PARTITION_GROUP, 1)));
  }

  static RaftPartitionConfig createConfig(final boolean receiveOnLegacySubject) {
    final var raftPartitionConfig = new RaftPartitionConfig();
    raftPartitionConfig.setStorageConfig(new RaftStorageConfig());
    raftPartitionConfig.setReceiveOnLegacySubject(receiveOnLegacySubject);
    return raftPartitionConfig;
  }
}
