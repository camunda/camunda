/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.atomix.raft.RaftError;
import io.atomix.raft.cluster.FenceEntry;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ForceConfigureRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.utils.serializer.Serializer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Raft requests are serialized with Kryo. Verifies that the fields added for fencing and voter
 * policies survive a round trip, i.e. that the new types are registered and serializable.
 */
final class RaftNamespacesTest {

  private final Serializer serializer = Serializer.using(RaftNamespaces.RAFT_PROTOCOL);
  private final List<FenceEntry> fenced = List.of(new FenceEntry(MemberId.from("b_2"), 42L));
  private final Set<RaftMember> members =
      new HashSet<>(
          List.of(
              new DefaultRaftMember(MemberId.from("a_1"), Type.ACTIVE, Instant.now(), 0L),
              new DefaultRaftMember(MemberId.from("b_2"), Type.PASSIVE, Instant.now(), 7L)));

  private <T> T roundTrip(final T object) {
    return serializer.decode(serializer.encode(object));
  }

  @Test
  void shouldSerializeReconfigureRequest() {
    // given
    final var request =
        ReconfigureRequest.builder()
            .withIndex(3)
            .withTerm(2)
            .withMembers(members)
            .withFenced(fenced)
            .withMembershipVersion(9)
            .withExactIndex(true)
            .withReadOnly(true)
            .from("a_1")
            .build();
    request.setSenderIncarnation(5L);

    // when
    final ReconfigureRequest read = roundTrip(request);

    // then
    assertThat(read.exactIndex()).isTrue();
    assertThat(read.readOnly()).isTrue();
    assertThat(read.fenced()).containsExactlyElementsOf(fenced);
    assertThat(read.membershipVersion()).isEqualTo(9);
    assertThat(read.senderIncarnation()).isEqualTo(5L);
    assertThat(read.members())
        .extracting(RaftMember::memberId, RaftMember::getIncarnation)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(MemberId.from("a_1"), 0L),
            org.assertj.core.groups.Tuple.tuple(MemberId.from("b_2"), 7L));
  }

  @Test
  void shouldSerializeReconfigureRejectionWithConfiguration() {
    // given
    final var response =
        ReconfigureResponse.builder()
            .withStatus(Status.ERROR)
            .withError(RaftError.Type.CONFIGURATION_ERROR, "stale")
            .withIndex(3)
            .withTerm(2)
            .withTime(1)
            .withMembers(members)
            .withFenced(fenced)
            .withMembershipVersion(9)
            .withOldMembers(members)
            .withCommitted(true)
            .withLeader("a_1")
            .build();

    // when
    final ReconfigureResponse read = roundTrip(response);

    // then
    assertThat(read.index()).isEqualTo(3);
    assertThat(read.members()).hasSize(2);
    assertThat(read.fenced()).containsExactlyElementsOf(fenced);
    assertThat(read.membershipVersion()).isEqualTo(9);
    assertThat(read.oldMembers()).hasSize(2);
    assertThat(read.committed()).isTrue();
    assertThat(read.leader()).isEqualTo("a_1");
  }

  @Test
  void shouldSerializeConfigureRequest() {
    // given
    final var request =
        ConfigureRequest.builder()
            .withTerm(2)
            .withLeader(MemberId.from("a_1"))
            .withIndex(3)
            .withTime(1)
            .withNewMembers(members)
            .withOldMembers(new HashSet<>())
            .withFenced(fenced)
            .withMembershipVersion(9)
            .withCommitted(true)
            .build();

    // when
    final ConfigureRequest read = roundTrip(request);

    // then
    assertThat(read.fenced()).containsExactlyElementsOf(fenced);
    assertThat(read.membershipVersion()).isEqualTo(9);
    assertThat(read.committed()).isTrue();
  }

  @Test
  void shouldSerializeForceConfigureRequest() {
    // given
    final var request =
        ForceConfigureRequest.builder()
            .withTerm(2)
            .withIndex(3)
            .withTime(1)
            .withNewMembers(members)
            .withFenced(fenced)
            .withMembershipVersion(9)
            .from(MemberId.from("a_1"))
            .build();

    // when
    final ForceConfigureRequest read = roundTrip(request);

    // then
    assertThat(read.fenced()).containsExactlyElementsOf(fenced);
    assertThat(read.membershipVersion()).isEqualTo(9);
  }

  @Test
  void shouldSerializeResponseSenderIncarnation() {
    // given
    final var response =
        PollResponse.builder().withStatus(Status.OK).withTerm(1).withAccepted(true).build();
    response.setSenderIncarnation(9L);

    // when
    final PollResponse read = roundTrip(response);

    // then
    assertThat(read.senderIncarnation()).isEqualTo(9L);
  }

  /**
   * Kryo's record serializer writes the components by position, so that a later version could not
   * add a field without breaking older members. The compatible field serializer writes each field
   * with its name, and a reader skips the fields that it does not know.
   */
  @Test
  void shouldSerializeFenceEntryWithFieldNames() {
    // when
    final var fenceEntry =
        new String(serializer.encode(fenced.getFirst()), StandardCharsets.ISO_8859_1);

    // then - Kryo marks the last character of an ASCII string with the high bit
    assertThat(fenceEntry).contains("memberI").contains("incarnatio");
  }
}
