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
    final var initiateRequest = initiate(MemberId.from("2"), MemberId.from("1"), 7);
    final var initiateResponse =
        LeadershipTransferInitiateResponse.builder()
            .withStatus(Status.OK)
            .withAccepted(false)
            .withResult(LeadershipTransferResult.LAG_TOO_HIGH)
            .withLeader(MemberId.from("3"))
            .build();
    final var resultRequest =
        LeadershipTransferResultRequest.builder()
            .withLeader(MemberId.from("3"))
            .withDesiredLeader(MemberId.from("2"))
            .withResult(LeadershipTransferResult.TRANSFERRED)
            .build();

    // when / then
    assertThat(roundTrip(initiateRequest)).isEqualTo(initiateRequest);
    final LeadershipTransferInitiateResponse deserializedResponse = roundTrip(initiateResponse);
    assertThat(deserializedResponse.accepted()).isFalse();
    assertThat(deserializedResponse.result()).isEqualTo(LeadershipTransferResult.LAG_TOO_HIGH);
    assertThat(deserializedResponse.leader()).isEqualTo(MemberId.from("3"));
    assertThat(roundTrip(resultRequest)).isEqualTo(resultRequest);
  }

  private static <T> T roundTrip(final T message) {
    return RaftNamespaces.RAFT_PROTOCOL.deserialize(
        RaftNamespaces.RAFT_PROTOCOL.serialize(message));
  }

  private LeadershipTransferInitiateRequest initiate(
      final MemberId desiredLeader, final MemberId coordinator, final long configVersion) {
    return LeadershipTransferInitiateRequest.builder()
        .withDesiredLeader(desiredLeader)
        .withCoordinator(coordinator)
        .withCoordinatorConfigVersion(configVersion)
        .build();
  }
}
