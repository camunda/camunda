/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MembershipConfig;
import io.atomix.cluster.MulticastConfig;
import io.atomix.cluster.discovery.MulticastDiscoveryConfig;
import io.atomix.cluster.discovery.MulticastDiscoveryProvider;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.protocol.HeartbeatMembershipProtocolConfig;
import io.atomix.core.profile.ConsensusProfile;
import io.atomix.core.profile.ConsensusProfileConfig;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.raft.partition.RaftPartitionGroupConfig;
import io.atomix.utils.memory.MemorySize;
import java.time.Duration;
import java.util.Arrays;
import org.junit.Test;

/** Atomix configuration test. */
public class AtomixConfigTest {

  @Test
  public void testDefaultAtomixConfig() throws Exception {
    final AtomixConfig config = Atomix.config();
    assertTrue(config.getPartitionGroups().isEmpty());
    assertTrue(config.getProfiles().isEmpty());
  }

  @Test
  public void testAtomixConfig() throws Exception {
    final AtomixConfig config =
        Atomix.config(getClass().getClassLoader().getResource("test.conf").getPath());

    final ClusterConfig cluster = config.getClusterConfig();
    assertEquals("test", cluster.getClusterId());

    final MemberConfig node = cluster.getNodeConfig();
    assertEquals("one", node.getId().id());
    assertEquals("localhost:5000", node.getAddress().toString());
    assertEquals("foo", node.getZoneId());
    assertEquals("bar", node.getRackId());
    assertEquals("baz", node.getHostId());
    assertEquals("bar", node.getProperties().getProperty("foo"));
    assertEquals("baz", node.getProperties().getProperty("bar"));

    final MulticastConfig multicast = cluster.getMulticastConfig();
    assertTrue(multicast.isEnabled());
    assertEquals("230.0.1.1", multicast.getGroup().getHostAddress());
    assertEquals(56789, multicast.getPort());

    final HeartbeatMembershipProtocolConfig protocol =
        (HeartbeatMembershipProtocolConfig) cluster.getProtocolConfig();
    assertEquals(Duration.ofMillis(200), protocol.getHeartbeatInterval());
    assertEquals(12, protocol.getPhiFailureThreshold());
    assertEquals(Duration.ofSeconds(15), protocol.getFailureTimeout());

    final MembershipConfig membership = cluster.getMembershipConfig();
    assertEquals(Duration.ofSeconds(1), membership.getBroadcastInterval());
    assertEquals(12, membership.getReachabilityThreshold());
    assertEquals(Duration.ofSeconds(15), membership.getReachabilityTimeout());

    final MulticastDiscoveryConfig discovery =
        (MulticastDiscoveryConfig) cluster.getDiscoveryConfig();
    assertEquals(MulticastDiscoveryProvider.TYPE, discovery.getType());
    assertEquals(Duration.ofSeconds(1), discovery.getBroadcastInterval());
    assertEquals(12, discovery.getFailureThreshold());
    assertEquals(Duration.ofSeconds(15), discovery.getFailureTimeout());

    final MessagingConfig messaging = cluster.getMessagingConfig();
    assertEquals(2, messaging.getInterfaces().size());
    assertEquals("127.0.0.1", messaging.getInterfaces().get(0));
    assertEquals("0.0.0.0", messaging.getInterfaces().get(1));
    assertEquals(5000, messaging.getPort().intValue());
    assertEquals(Duration.ofSeconds(10), messaging.getConnectTimeout());
    assertTrue(messaging.getTlsConfig().isEnabled());
    assertEquals("keystore.jks", messaging.getTlsConfig().getKeyStore());
    assertEquals("foo", messaging.getTlsConfig().getKeyStorePassword());
    assertEquals("truststore.jks", messaging.getTlsConfig().getTrustStore());
    assertEquals("bar", messaging.getTlsConfig().getTrustStorePassword());

    final RaftPartitionGroupConfig managementGroup =
        (RaftPartitionGroupConfig) config.getManagementGroup();
    assertEquals(RaftPartitionGroup.TYPE, managementGroup.getType());
    assertEquals(1, managementGroup.getPartitions());
    assertEquals(Duration.ofSeconds(5), managementGroup.getElectionTimeout());
    assertEquals(Duration.ofMillis(500), managementGroup.getHeartbeatInterval());
    assertEquals(Duration.ofSeconds(10), managementGroup.getDefaultSessionTimeout());
    assertEquals(
        new MemorySize(1024 * 1024 * 16), managementGroup.getStorageConfig().getSegmentSize());

    final RaftPartitionGroupConfig groupOne =
        (RaftPartitionGroupConfig) config.getPartitionGroups().get("one");
    assertEquals(RaftPartitionGroup.TYPE, groupOne.getType());
    assertEquals("one", groupOne.getName());
    assertEquals(7, groupOne.getPartitions());

    final ConsensusProfileConfig consensusProfile =
        (ConsensusProfileConfig) config.getProfiles().get(0);
    assertEquals(ConsensusProfile.TYPE, consensusProfile.getType());
    assertEquals("management", consensusProfile.getManagementGroup());
    assertEquals("consensus", consensusProfile.getDataGroup());
    assertEquals(3, consensusProfile.getPartitions());
    assertTrue(consensusProfile.getMembers().containsAll(Arrays.asList("one", "two", "three")));
  }
}
