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

import io.atomix.cluster.MemberId;
import java.util.ArrayList;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;

public class RaftBootstrapTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldNotStartWithDifferentClusterSize() throws Exception {
    // given three nodes cluster
    final var memberIds = raftRule.getMemberIds();
    final var newMemberIds = new ArrayList<MemberId>(memberIds);
    newMemberIds.add(MemberId.from("4"));

    // when
    raftRule.shutdownServer("1");

    // expected
    Assertions.assertThatThrownBy(() -> raftRule.bootstrapNodeWithMemberIds("1", newMemberIds))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Expected that persisted cluster size '3' is equal to given one '4', but was different.");
  }

  @Test
  public void shouldNotStartWithDifferentIds() throws Exception {
    // given three nodes cluster
    final var memberIds = raftRule.getMemberIds();
    final var newMemberIds = new ArrayList<MemberId>(memberIds);
    newMemberIds.remove(0);
    newMemberIds.add(MemberId.from("4"));

    // when
    raftRule.shutdownServer("1");

    // expected
    Assertions.assertThatThrownBy(() -> raftRule.bootstrapNodeWithMemberIds("1", newMemberIds))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Expected to find given node id '4' in persisted members");
  }
}
