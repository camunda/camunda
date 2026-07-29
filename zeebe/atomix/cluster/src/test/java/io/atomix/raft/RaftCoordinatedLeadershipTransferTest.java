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
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.atomix.raft.partition.impl.RaftNamespaces;
import io.atomix.raft.protocol.LeadershipTransferInitiateRequest;
import io.atomix.raft.protocol.LeadershipTransferInitiateResponse;
import io.atomix.raft.protocol.LeadershipTransferResultRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import org.junit.Test;

public class RaftCoordinatedLeadershipTransferTest {

  @Test
  public void shouldRoundTripTransferMessagesThroughRaftNamespace() {
    // given
    final var initiateRequest = initiate(MemberId.from("2"), MemberId.from("1"), 7, 0x5eed_0005L);
    final var acceptedResponse =
        LeadershipTransferInitiateResponse.builder().withStatus(Status.OK).build();
    final var rejectedResponse =
        LeadershipTransferInitiateResponse.builder()
            .withStatus(Status.OK)
            .withRejectionReason(LeadershipTransferResult.LAG_TOO_HIGH)
            .build();
    final var notLeaderResponse =
        LeadershipTransferInitiateResponse.builder()
            .withStatus(Status.ERROR)
            .withError(RaftError.Type.ILLEGAL_MEMBER_STATE)
            .build();
    final var resultRequest =
        result(
            MemberId.from("3"),
            MemberId.from("2"),
            LeadershipTransferResult.TRANSFERRED,
            0x5eed_0005L);

    // when / then
    assertThat(roundTrip(initiateRequest)).isEqualTo(initiateRequest);
    assertThat(roundTrip(initiateRequest).correlationId()).isEqualTo(0x5eed_0005L);
    assertThat(roundTrip(acceptedResponse).accepted()).isTrue();
    final LeadershipTransferInitiateResponse deserializedRejection = roundTrip(rejectedResponse);
    assertThat(deserializedRejection.accepted()).isFalse();
    assertThat(deserializedRejection.rejectionReason())
        .isEqualTo(LeadershipTransferResult.LAG_TOO_HIGH);
    final LeadershipTransferInitiateResponse deserializedNotLeader = roundTrip(notLeaderResponse);
    assertThat(deserializedNotLeader.accepted()).isFalse();
    assertThat(deserializedNotLeader.error().type()).isEqualTo(RaftError.Type.ILLEGAL_MEMBER_STATE);
    assertThat(roundTrip(resultRequest)).isEqualTo(resultRequest);
    assertThat(roundTrip(resultRequest).correlationId()).isEqualTo(0x5eed_0005L);
  }

  @Test
  public void shouldTellRequestsApartByCorrelationId() {
    // given
    final var initiateRequest = initiate(MemberId.from("2"), MemberId.from("1"), 7, 0x5eed_0006L);
    final var sameInitiateRequest =
        initiate(MemberId.from("2"), MemberId.from("1"), 7, 0x5eed_0006L);
    final var otherInitiateRequest =
        initiate(MemberId.from("2"), MemberId.from("1"), 7, 0x5eed_0007L);
    final var resultRequest =
        result(
            MemberId.from("3"),
            MemberId.from("2"),
            LeadershipTransferResult.TRANSFERRED,
            0x5eed_0006L);
    final var sameResultRequest =
        result(
            MemberId.from("3"),
            MemberId.from("2"),
            LeadershipTransferResult.TRANSFERRED,
            0x5eed_0006L);
    final var otherResultRequest =
        result(
            MemberId.from("3"),
            MemberId.from("2"),
            LeadershipTransferResult.TRANSFERRED,
            0x5eed_0007L);

    // then
    assertThat(initiateRequest.correlationId()).isEqualTo(0x5eed_0006L);
    assertThat(initiateRequest)
        .isEqualTo(sameInitiateRequest)
        .hasSameHashCodeAs(sameInitiateRequest)
        .isNotEqualTo(otherInitiateRequest);
    assertThat(resultRequest.correlationId()).isEqualTo(0x5eed_0006L);
    assertThat(resultRequest)
        .isEqualTo(sameResultRequest)
        .hasSameHashCodeAs(sameResultRequest)
        .isNotEqualTo(otherResultRequest);
  }

  @Test
  public void shouldRejectRequestsBuiltWithoutACorrelationId() {
    // given
    final var initiateBuilder =
        LeadershipTransferInitiateRequest.builder()
            .withDesiredLeader(MemberId.from("2"))
            .withCoordinator(MemberId.from("1"))
            .withCoordinatorConfigIndex(7);
    final var resultBuilder =
        LeadershipTransferResultRequest.builder()
            .withLeader(MemberId.from("3"))
            .withDesiredLeader(MemberId.from("2"))
            .withResult(LeadershipTransferResult.TRANSFERRED);

    // when / then
    assertThatThrownBy(initiateBuilder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correlationId");
    assertThatThrownBy(resultBuilder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correlationId");
  }

  private static <T> T roundTrip(final T message) {
    return RaftNamespaces.RAFT_PROTOCOL.deserialize(
        RaftNamespaces.RAFT_PROTOCOL.serialize(message));
  }

  private LeadershipTransferInitiateRequest initiate(
      final MemberId desiredLeader,
      final MemberId coordinator,
      final long configIndex,
      final long correlationId) {
    return LeadershipTransferInitiateRequest.builder()
        .withDesiredLeader(desiredLeader)
        .withCoordinator(coordinator)
        .withCoordinatorConfigIndex(configIndex)
        .withCorrelationId(correlationId)
        .build();
  }

  private static LeadershipTransferResultRequest result(
      final MemberId leader,
      final MemberId desiredLeader,
      final LeadershipTransferResult result,
      final long correlationId) {
    return LeadershipTransferResultRequest.builder()
        .withLeader(leader)
        .withDesiredLeader(desiredLeader)
        .withResult(result)
        .withCorrelationId(correlationId)
        .build();
  }
}
