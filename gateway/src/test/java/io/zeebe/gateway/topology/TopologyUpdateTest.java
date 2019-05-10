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
package io.zeebe.gateway.topology;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.protocol.impl.data.cluster.BrokerInfo;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class TopologyUpdateTest {

  @Rule public ActorSchedulerRule actor = new ActorSchedulerRule();

  private BrokerTopologyManagerImpl topologyManager;

  @Before
  public void setUp() {
    topologyManager = new BrokerTopologyManagerImpl((a, b) -> {});
    actor.submitActor(topologyManager);
  }

  @After
  public void tearDown() {
    topologyManager.close();
  }

  @Test
  public void shouldUpdateTopologyOnBrokerAdd() {
    final BrokerInfo broker = createBroker(0);
    topologyManager.event(createMemberAddedEvent(broker));
    waitUntil(() -> topologyManager.getTopology() != null);
    assertThat(topologyManager.getTopology().getFollowersForPartition(1)).isNull();

    final BrokerInfo brokerUpdated = createBroker(0);
    brokerUpdated.addFollowerForPartition(1);
    topologyManager.event(createMemberUpdateEvent(brokerUpdated));
    waitUntil(() -> topologyManager.getTopology().getFollowersForPartition(1) != null);
    assertThat(topologyManager.getTopology().getFollowersForPartition(1).contains(0)).isTrue();
  }

  @Test
  public void shouldUpdateTopologyOnBrokerRemove() {
    final BrokerInfo broker = createBroker(0);
    topologyManager.event(createMemberAddedEvent(broker));
    final BrokerInfo brokerUpdated = createBroker(0);
    brokerUpdated.addFollowerForPartition(1);
    topologyManager.event(createMemberUpdateEvent(brokerUpdated));
    waitUntil(() -> topologyManager.getTopology() != null);

    topologyManager.event(createMemberRemoveEvent(brokerUpdated));
    waitUntil(() -> topologyManager.getTopology().getBrokers().isEmpty());

    topologyManager.event(createMemberAddedEvent(broker));
    waitUntil(() -> !topologyManager.getTopology().getBrokers().isEmpty());

    assertThat(topologyManager.getTopology().getFollowersForPartition(1).contains(0)).isFalse();
  }

  private BrokerInfo createBroker(int brokerId) {
    final BrokerInfo broker = new BrokerInfo(brokerId, 1, 3, 3);
    broker.setApiAddress(BrokerInfo.CLIENT_API_PROPERTY, "localhost:1000");
    return broker;
  }

  private ClusterMembershipEvent createMemberAddedEvent(BrokerInfo broker) {
    final Member member = new Member(new MemberConfig());
    BrokerInfo.writeIntoProperties(member.properties(), broker);
    return new ClusterMembershipEvent(Type.MEMBER_ADDED, member);
  }

  private ClusterMembershipEvent createMemberUpdateEvent(BrokerInfo broker) {
    final Member member = new Member(new MemberConfig());
    BrokerInfo.writeIntoProperties(member.properties(), broker);
    return new ClusterMembershipEvent(Type.METADATA_CHANGED, member);
  }

  private ClusterMembershipEvent createMemberRemoveEvent(BrokerInfo broker) {
    final Member member = new Member(new MemberConfig());
    BrokerInfo.writeIntoProperties(member.properties(), broker);
    return new ClusterMembershipEvent(Type.MEMBER_REMOVED, member);
  }
}
