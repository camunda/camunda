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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.atomix.raft.primitive.TestMember;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ForceConfigureRequest;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.RaftServerProtocol;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
public class RaftServerSenderSubjectsTest {

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

  private RaftPartitionConfig createRaftPartitionConfig(final boolean sendOnLegacySubject) {
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var raftStorageConfig = new RaftStorageConfig();

    raftPartitionConfig.setSendOnLegacySubject(sendOnLegacySubject);
    raftPartitionConfig.setStorageConfig(raftStorageConfig);

    return raftPartitionConfig;
  }

  @ParameterizedTest
  @MethodSource("provideScenarios")
  void shouldCallWithCorrectSubject(
      final boolean sendOnLegacySubject,
      final String subjectSuffix,
      final Consumer<RaftServerProtocol> applier,
      @TempDir final Path tempDir) {
    // given
    final var config = createRaftPartitionConfig(sendOnLegacySubject);
    final var server = createRaftPartitionServer(config, tempDir);
    final var protocol = server.getServer().getContext().getProtocol();

    final var prefixSubject =
        PARTITION_NAME_FORMAT.formatted(
            sendOnLegacySubject ? PARTITION_GROUP : config.getEngineName(), 1);
    final var subject = "%s-%s".formatted(prefixSubject, subjectSuffix);

    // when
    applier.accept(protocol);

    // then
    verify(clusterCommunicationService, times(1))
        .send(eq(subject), any(), any(), any(), any(), any());
  }

  static Stream<Arguments> provideScenarios() {
    return Stream.of(
        Arguments.of(true, "append", applyAppendRequestV1()),
        Arguments.of(true, "append-versioned", applyAppendRequestV2()),
        Arguments.of(true, "configure", configureRequest()),
        Arguments.of(true, "force-configure", forceConfigureRequest()),
        Arguments.of(true, "install", installRequest()),
        Arguments.of(true, "join", joinRequest()),
        Arguments.of(true, "leave", leaveRequest()),
        Arguments.of(true, "poll", pollRequest()),
        Arguments.of(true, "reconfigure", reconfigureRequest()),
        Arguments.of(true, "vote", voteRequest()),
        Arguments.of(true, "transfer", transferRequest()),
        Arguments.of(false, "append", applyAppendRequestV1()),
        Arguments.of(false, "append-versioned", applyAppendRequestV2()),
        Arguments.of(false, "configure", configureRequest()),
        Arguments.of(false, "force-configure", forceConfigureRequest()),
        Arguments.of(false, "install", installRequest()),
        Arguments.of(false, "join", joinRequest()),
        Arguments.of(false, "leave", leaveRequest()),
        Arguments.of(false, "poll", pollRequest()),
        Arguments.of(false, "reconfigure", reconfigureRequest()),
        Arguments.of(false, "vote", voteRequest()),
        Arguments.of(false, "transfer", transferRequest()));
  }

  static Consumer<RaftServerProtocol> applyAppendRequestV1() {
    return p -> p.append(MEMBER_ID, new AppendRequest(2, "a", 0, 0, List.of(), 1));
  }

  static Consumer<RaftServerProtocol> applyAppendRequestV2() {
    return p -> p.append(MEMBER_ID, new VersionedAppendRequest(2, 1, "a", 0, 0, List.of(), 1));
  }

  static Consumer<RaftServerProtocol> configureRequest() {
    return p ->
        p.configure(
            MEMBER_ID,
            new ConfigureRequest(1, "b", 0, 0, Collections.emptyList(), Collections.emptyList()));
  }

  static Consumer<RaftServerProtocol> forceConfigureRequest() {
    return p ->
        p.forceConfigure(
            MEMBER_ID, new ForceConfigureRequest(1, 0, 0, Collections.emptySet(), "a"));
  }

  static Consumer<RaftServerProtocol> installRequest() {
    return p ->
        p.install(
            MEMBER_ID, new InstallRequest(1, MEMBER_ID, 0, 0, 0, null, null, null, true, false));
  }

  static Consumer<RaftServerProtocol> joinRequest() {
    return p ->
        p.join(
            MEMBER_ID,
            JoinRequest.builder()
                .withJoiningMember(new TestMember(MEMBER_ID, Type.ACTIVE))
                .build());
  }

  static Consumer<RaftServerProtocol> leaveRequest() {
    return p ->
        p.leave(
            MEMBER_ID,
            LeaveRequest.builder()
                .withLeavingMember(new TestMember(MEMBER_ID, Type.ACTIVE))
                .build());
  }

  static Consumer<RaftServerProtocol> pollRequest() {
    return p -> p.poll(MEMBER_ID, new PollRequest(0, "a", 0, 0));
  }

  static Consumer<RaftServerProtocol> reconfigureRequest() {
    return p ->
        p.reconfigure(MEMBER_ID, new ReconfigureRequest(Collections.emptyList(), 0, 0, "b"));
  }

  static Consumer<RaftServerProtocol> voteRequest() {
    return p -> p.vote(MEMBER_ID, new VoteRequest(0, "b", 0, 0));
  }

  static Consumer<RaftServerProtocol> transferRequest() {
    return p -> p.transfer(MEMBER_ID, TransferRequest.builder().withMember(MEMBER_ID).build());
  }
}
