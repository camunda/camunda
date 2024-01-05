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
import io.atomix.raft.RaftRule.Configurator;
import io.atomix.raft.RaftServer.Builder;
import io.atomix.raft.partition.RaftElectionConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RaftPriorityElectionTest {

  @Rule @Parameter public RaftRule raftRule;

  @Parameters(name = "{index}: {0}")
  public static Object[][] raftConfigurations() {
    return new Object[][] {
      new Object[] {
        RaftRule.withBootstrappedNodes(
            3,
            new Configurator() {
              @Override
              public void configure(final MemberId id, final Builder builder) {
                builder.withElectionConfig(
                    RaftElectionConfig.ofPriorityElection(null, 3, Integer.parseInt(id.id()) + 1));
              }
            })
      },
      new Object[] {
        RaftRule.withBootstrappedNodes(
            4,
            new Configurator() {
              @Override
              public void configure(final MemberId id, final Builder builder) {
                builder.withElectionConfig(
                    RaftElectionConfig.ofPriorityElection(null, 4, Integer.parseInt(id.id()) + 1));
              }
            })
      },
      new Object[] {
        RaftRule.withBootstrappedNodes(
            5,
            new Configurator() {
              @Override
              public void configure(final MemberId id, final Builder builder) {
                builder.withElectionConfig(
                    RaftElectionConfig.ofPriorityElection(null, 5, Integer.parseInt(id.id()) + 1));
              }
            })
      }
    };
  }

  // Note: Priority election is not deterministic hence we cannot deterministically test if leaders
  // are elected according to the priority. Instead here we only test that leader election succeeds.
  @Test
  public void shouldElectNewLeadersWhenLeaderUnavailable() throws Throwable {
    // given
    final int failureTolerance = (raftRule.getMemberIds().size() - 1) / 2;

    for (int i = 0; i < failureTolerance; i++) {
      raftRule.appendEntries(1);
      // when
      raftRule.shutdownLeader();

      // then
      raftRule.awaitNewLeader();
    }
  }
}
