/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.partition.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftError.Type;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.ExporterPosition;
import io.atomix.raft.protocol.ForceConfigureRequest;
import io.atomix.raft.protocol.ForceConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.JoinResponse;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.LeadershipTransferResultResponse;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.ReplicatableJournalRecord;
import io.atomix.raft.protocol.TimeoutNowRequest;
import io.atomix.raft.protocol.TimeoutNowResponse;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespace.Builder;
import io.atomix.utils.serializer.Namespaces;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TimeoutNowRequestSerializationTest {

  private static final Namespace LEGACY_RAFT_PROTOCOL = legacyRaftProtocol();
  private static final MemberId LEADER = MemberId.from("1");
  private static final List<ExporterPosition> EXPORTER_POSITIONS =
      List.of(
          new ExporterPosition("exporter-1", 10, new byte[] {1, 2, 3}),
          new ExporterPosition("exporter-2", 20, new byte[0]));

  @Test
  void shouldDecodeARequestWithExporterPositionsOnANodeThatDoesNotKnowThem() {
    // given
    final var request =
        TimeoutNowRequest.builder()
            .withTerm(3)
            .withLeader(LEADER)
            .withExporterPositions(EXPORTER_POSITIONS)
            .build();

    // when
    final LegacyTimeoutNowRequest decoded =
        LEGACY_RAFT_PROTOCOL.deserialize(RaftNamespaces.RAFT_PROTOCOL.serialize(request));

    // then
    assertThat(decoded.term).isEqualTo(3);
    assertThat(decoded.leader).isEqualTo(LEADER);
  }

  @Test
  void shouldDecodeARequestFromANodeThatDoesNotKnowTheExporterPositions() {
    // given
    final var legacy = new LegacyTimeoutNowRequest(3, LEADER);

    // when
    final TimeoutNowRequest decoded =
        RaftNamespaces.RAFT_PROTOCOL.deserialize(LEGACY_RAFT_PROTOCOL.serialize(legacy));

    // then
    assertThat(decoded.term()).isEqualTo(3);
    assertThat(decoded.leader()).isEqualTo(LEADER);
    assertThat(decoded.exporterPositions()).isEmpty();
  }

  @Test
  void shouldRoundTripTheExporterPositions() {
    // given
    final var request =
        TimeoutNowRequest.builder()
            .withTerm(3)
            .withLeader(LEADER)
            .withExporterPositions(EXPORTER_POSITIONS)
            .build();

    // when
    final TimeoutNowRequest decoded =
        RaftNamespaces.RAFT_PROTOCOL.deserialize(RaftNamespaces.RAFT_PROTOCOL.serialize(request));

    // then
    assertThat(decoded).isEqualTo(request);
    assertThat(decoded.exporterPositions()).isEqualTo(EXPORTER_POSITIONS);
  }

  /**
   * {@link RaftNamespaces#RAFT_PROTOCOL} before TimeoutNow carried exporter positions, and so
   * before {@link ExporterPosition} was registered.
   */
  private static Namespace legacyRaftProtocol() {
    return new Builder()
        .register(Namespaces.BASIC)
        .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
        .register(ConfigureRequest.class)
        .register(ConfigureResponse.class)
        .register(ReconfigureRequest.class)
        .register(ReconfigureResponse.class)
        .register(InstallRequest.class)
        .register(InstallResponse.class)
        .register(PollRequest.class)
        .register(PollResponse.class)
        .register(VoteRequest.class)
        .register(VoteResponse.class)
        .register(AppendRequest.class)
        .register(AppendResponse.class)
        .register(Status.class)
        .register(RaftError.class)
        .register(Type.class)
        .nextId(517)
        .register(ArrayList.class)
        .register(LinkedList.class)
        .register(Collections.emptyList().getClass())
        .register(HashSet.class)
        .register(DefaultRaftMember.class)
        .register(MemberId.class)
        .register(RaftMember.Type.class)
        .register(Instant.class)
        .nextId(528)
        .register(PersistedRaftRecord.class)
        .register(TransferRequest.class)
        .register(TransferResponse.class)
        .register(VersionedAppendRequest.class)
        .register(ReplicatableJournalRecord.class)
        .register(JoinRequest.class)
        .register(JoinResponse.class)
        .register(LeaveRequest.class)
        .register(LeaveResponse.class)
        .register(ForceConfigureRequest.class)
        .register(ForceConfigureResponse.class)
        .register(LegacyTimeoutNowRequest.class)
        .register(TimeoutNowResponse.class)
        .register(LeadershipTransferInitiateRequest.class)
        .register(LeadershipTransferInitiateResponse.class)
        .register(LeadershipTransferResultRequest.class)
        .register(LeadershipTransferResultResponse.class)
        .register(LeadershipTransferResult.class)
        .name("LegacyRaftProtocol")
        .build();
  }

  private static final class LegacyTimeoutNowRequest {
    private final long term;
    private final MemberId leader;

    private LegacyTimeoutNowRequest(final long term, final MemberId leader) {
      this.term = term;
      this.leader = leader;
    }
  }
}
