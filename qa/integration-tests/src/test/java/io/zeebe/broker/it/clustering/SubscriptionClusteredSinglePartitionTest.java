/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.broker.it.clustering;

import static io.zeebe.broker.it.clustering.ClusteringRule.BROKER_2_TOML;
import static io.zeebe.broker.it.clustering.ClusteringRule.BROKER_3_TOML;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.events.RaftEvent;
import io.zeebe.gateway.api.events.RaftState;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class SubscriptionClusteredSinglePartitionTest {

  public Timeout testTimeout = Timeout.seconds(30);
  public ClusteringRule clusteringRule =
      new ClusteringRule(
          new String[] {"zeebe.cluster.1.singlePartition.cfg.toml", BROKER_2_TOML, BROKER_3_TOML});
  public ClientRule clientRule = new ClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  private ZeebeClient client;

  @Before
  public void startUp() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldReceiveRaftEvents() {
    // given
    clusteringRule.restartBroker(clusteringRule.getFollowerOnly());

    // when
    final List<RaftEvent> raftEvents = new ArrayList<>();
    client
        .topicClient()
        .newSubscription()
        .name("test-subscription")
        .raftEventHandler(raftEvents::add)
        .startAtHeadOfTopic()
        .open();

    // then we should receive two raft add member events
    waitUntil(() -> raftEvents.size() == 4);

    assertThat(raftEvents).hasSize(4);
    assertThat(raftEvents)
        .extracting(RaftEvent::getState)
        .containsExactly(
            RaftState.MEMBER_ADDED,
            RaftState.MEMBER_ADDED,
            RaftState.MEMBER_REMOVED,
            RaftState.MEMBER_ADDED);
    assertThat(raftEvents.get(1).getMembers()).hasSize(clusteringRule.getBrokersInCluster().size());
  }

  @Test
  public void shouldReceiveRaftEventsFromSystemTopic() {
    // given
    clusteringRule.restartBroker(clusteringRule.getFollowerOnly());

    // when
    final List<RaftEvent> raftEvents = new ArrayList<>();
    client
        .newManagementSubscription()
        .name("test-subscription")
        .raftEventHandler(raftEvents::add)
        .startAtHeadOfTopic()
        .open();

    // then we should receive two raft add member events
    waitUntil(() -> raftEvents.size() == 4);

    assertThat(raftEvents).hasSize(4);
    assertThat(raftEvents)
        .extracting(RaftEvent::getState)
        .containsExactly(
            RaftState.MEMBER_ADDED,
            RaftState.MEMBER_ADDED,
            RaftState.MEMBER_REMOVED,
            RaftState.MEMBER_ADDED);
    assertThat(raftEvents.get(1).getMembers()).hasSize(clusteringRule.getBrokersInCluster().size());
  }
}
