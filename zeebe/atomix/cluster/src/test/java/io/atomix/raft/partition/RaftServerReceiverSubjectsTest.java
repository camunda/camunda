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
  private static final MemberId MEMBER_ID = new MemberId("0");
  private static final PartitionId PARTITION_ID = new PartitionId(PARTITION_GROUP, 1);
  private static final PartitionMetadata METADATA =
      new PartitionMetadata(PARTITION_ID, Set.of(), Map.of(), 1, MEMBER_ID);

  @Mock private ClusterMembershipService clusterMembershipService;
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  @Mock private ClusterCommunicationService clusterCommunicationService;
  @Mock private ReceivableSnapshotStore receivableSnapshotStore;

  private RaftPartitionServer createRaftPartitionServer(
      final RaftPartitionConfig raftPartitionConfig, final Path tempDir) {
    final var raftPartition =
        new RaftPartition(METADATA, raftPartitionConfig, tempDir.toFile(), meterRegistry);
    return new RaftPartitionServer(
        raftPartition,
        raftPartitionConfig,
        MEMBER_ID,
        clusterMembershipService,
        clusterCommunicationService,
        receivableSnapshotStore,
        METADATA,
        meterRegistry);
  }

  @ParameterizedTest
  @MethodSource("provideConfigurations")
  void shouldRegisterSubjects(final RaftPartitionConfig config, @TempDir final Path tempDir) {
    // given
    final var legacyReceiverSubjectsDisabled = config.isReceiveOnLegacySubject();
    final var engineName = config.getEngineName();

    // when
    createRaftPartitionServer(config, tempDir);

    // then
    assertLegacySubjectsRegistered(legacyReceiverSubjectsDisabled);
    assertEngineAwareSubjectsRegistered(engineName);
  }

  @ParameterizedTest
  @MethodSource("provideConfigurations")
  void shouldUnregisterSubjects(final RaftPartitionConfig config, @TempDir final Path tempDir) {
    // given
    final var server = createRaftPartitionServer(config, tempDir);
    final var receiveOnLegacySubject = config.isReceiveOnLegacySubject();
    final var engineName = config.getEngineName();

    // when
    server.stop().join();

    // then
    assertLegacySubjectsUnregistered(receiveOnLegacySubject);
    assertEngineAwareSubjectsUnregistered(engineName);
  }

  void assertLegacySubjectsRegistered(final boolean receiveOnLegacySubject) {
    assertSubjectsRegistered(PARTITION_GROUP, receiveOnLegacySubject ? 1 : 0);
  }

  void assertEngineAwareSubjectsRegistered(final String engineName) {
    assertSubjectsRegistered(engineName, 1);
  }

  void assertSubjectsRegistered(final String prefix, final int expected) {
    final var subjectPrefix = PARTITION_NAME_FORMAT.formatted(prefix, PARTITION_ID.id()) + "-%s";
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

  void assertLegacySubjectsUnregistered(final boolean receiveOnLegacySubject) {
    assertSubjectsUnregistered(PARTITION_GROUP, receiveOnLegacySubject ? 1 : 0);
  }

  void assertEngineAwareSubjectsUnregistered(final String engineName) {
    assertSubjectsUnregistered(engineName, 1);
  }

  void assertSubjectsUnregistered(final String prefix, final int expected) {
    final var partitionName = PARTITION_NAME_FORMAT.formatted(prefix, PARTITION_ID.id()) + "-%s";
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

  static Stream<Arguments> provideConfigurations() {
    return Stream.of(
        Arguments.of(createDefaultConfig()),
        Arguments.of(createConfigAndDisableReceiveOnLegacySubject()),
        Arguments.of(createConfigWithEngineName()),
        Arguments.of(createConfigWithEngineNameAndDisableReceiveOnLegacySubject()));
  }

  static RaftPartitionConfig createDefaultConfig() {
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var raftStorageConfig = new RaftStorageConfig();
    raftPartitionConfig.setStorageConfig(raftStorageConfig);
    return raftPartitionConfig;
  }

  static RaftPartitionConfig createConfigAndDisableReceiveOnLegacySubject() {
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var raftStorageConfig = new RaftStorageConfig();
    raftPartitionConfig.setReceiveOnLegacySubject(false);
    raftPartitionConfig.setStorageConfig(raftStorageConfig);
    return raftPartitionConfig;
  }

  static RaftPartitionConfig createConfigWithEngineName() {
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var raftStorageConfig = new RaftStorageConfig();
    raftPartitionConfig.setEngineName("foo");
    raftPartitionConfig.setStorageConfig(raftStorageConfig);
    return raftPartitionConfig;
  }

  static RaftPartitionConfig createConfigWithEngineNameAndDisableReceiveOnLegacySubject() {
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var raftStorageConfig = new RaftStorageConfig();
    raftPartitionConfig.setEngineName("foo");
    raftPartitionConfig.setReceiveOnLegacySubject(false);
    raftPartitionConfig.setStorageConfig(raftStorageConfig);
    return raftPartitionConfig;
  }
}
