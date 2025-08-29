/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.MembershipCfg;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class ClusterBrokerMembershipTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.membership.broadcast-updates=true",
        "camunda.cluster.membership.broadcast-disputes=false",
        "camunda.cluster.membership.notify-suspect=true",
        "camunda.cluster.membership.gossip-interval=500ms",
        "camunda.cluster.membership.gossip-fanout=4",
        "camunda.cluster.membership.probe-interval=2000ms",
        "camunda.cluster.membership.probe-timeout=200ms",
        "camunda.cluster.membership.suspect-probes=5",
        "camunda.cluster.membership.failure-timeout=20000ms",
        "camunda.cluster.membership.sync-interval=21000ms",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetMembershipFromNew() {
      assertThat(brokerCfg.getCluster().getMembership())
          .returns(true, MembershipCfg::isBroadcastUpdates)
          .returns(false, MembershipCfg::isBroadcastDisputes)
          .returns(true, MembershipCfg::isNotifySuspect)
          .returns(Duration.ofMillis(500), MembershipCfg::getGossipInterval)
          .returns(4, MembershipCfg::getGossipFanout)
          .returns(Duration.ofMillis(2000), MembershipCfg::getProbeInterval)
          .returns(Duration.ofMillis(200), MembershipCfg::getProbeTimeout)
          .returns(5, MembershipCfg::getSuspectProbes)
          .returns(Duration.ofMillis(20000), MembershipCfg::getFailureTimeout)
          .returns(Duration.ofMillis(21000), MembershipCfg::getSyncInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.cluster.membership.broadcastUpdates=true",
        "zeebe.gateway.cluster.membership.broadcastDisputes=false",
        "zeebe.gateway.cluster.membership.notifySuspect=true",
        "zeebe.gateway.cluster.membership.gossipInterval=501ms",
        "zeebe.gateway.cluster.membership.gossipFanout=5",
        "zeebe.gateway.cluster.membership.probeInterval=2001ms",
        "zeebe.gateway.cluster.membership.probeTimeout=201ms",
        "zeebe.gateway.cluster.membership.suspectProbes=6",
        "zeebe.gateway.cluster.membership.failureTimeout=20001ms",
        "zeebe.gateway.cluster.membership.syncInterval=21001ms"
      })
  class WithOnlyLegacyGatewayMembershipSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyGatewayMembershipSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetFromLegacyGatewayMembership() {
      assertThat(brokerCfg.getCluster().getMembership())
          .returns(false, MembershipCfg::isBroadcastUpdates)
          .returns(true, MembershipCfg::isBroadcastDisputes)
          .returns(false, MembershipCfg::isNotifySuspect)
          .returns(Duration.ofMillis(250), MembershipCfg::getGossipInterval)
          .returns(2, MembershipCfg::getGossipFanout)
          .returns(Duration.ofMillis(1000), MembershipCfg::getProbeInterval)
          .returns(Duration.ofMillis(100), MembershipCfg::getProbeTimeout)
          .returns(3, MembershipCfg::getSuspectProbes)
          .returns(Duration.ofMillis(10000), MembershipCfg::getFailureTimeout)
          .returns(Duration.ofMillis(10000), MembershipCfg::getSyncInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.cluster.membership.broadcastUpdates=true",
        "zeebe.broker.cluster.membership.broadcastDisputes=false",
        "zeebe.broker.cluster.membership.notifySuspect=true",
        "zeebe.broker.cluster.membership.gossipInterval=502ms",
        "zeebe.broker.cluster.membership.gossipFanout=7",
        "zeebe.broker.cluster.membership.probeInterval=2002ms",
        "zeebe.broker.cluster.membership.probeTimeout=202ms",
        "zeebe.broker.cluster.membership.suspectProbes=8",
        "zeebe.broker.cluster.membership.failureTimeout=20002ms",
        "zeebe.broker.cluster.membership.syncInterval=21002ms"
      })
  class WithOnlyLegacyBrokerMembershipSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyBrokerMembershipSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFromLegacyBrokerMembership() {
      assertThat(brokerCfg.getCluster().getMembership())
          .returns(true, MembershipCfg::isBroadcastUpdates)
          .returns(false, MembershipCfg::isBroadcastDisputes)
          .returns(true, MembershipCfg::isNotifySuspect)
          .returns(Duration.ofMillis(502), MembershipCfg::getGossipInterval)
          .returns(7, MembershipCfg::getGossipFanout)
          .returns(Duration.ofMillis(2002), MembershipCfg::getProbeInterval)
          .returns(Duration.ofMillis(202), MembershipCfg::getProbeTimeout)
          .returns(8, MembershipCfg::getSuspectProbes)
          .returns(Duration.ofMillis(20002), MembershipCfg::getFailureTimeout)
          .returns(Duration.ofMillis(21002), MembershipCfg::getSyncInterval);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.cluster.membership.broadcast-updates=true",
        "camunda.cluster.membership.broadcast-disputes=false",
        "camunda.cluster.membership.notify-suspect=true",
        "camunda.cluster.membership.gossip-interval=500ms",
        "camunda.cluster.membership.gossip-fanout=4",
        "camunda.cluster.membership.probe-interval=2000ms",
        "camunda.cluster.membership.probe-timeout=200ms",
        "camunda.cluster.membership.suspect-probes=5",
        "camunda.cluster.membership.failure-timeout=20000ms",
        "camunda.cluster.membership.sync-interval=21000ms",
        // legacy gateway configuration
        "zeebe.gateway.cluster.membership.broadcastUpdates=false",
        "zeebe.gateway.cluster.membership.broadcastDisputes=true",
        "zeebe.gateway.cluster.membership.notifySuspect=false",
        "zeebe.gateway.cluster.membership.gossipInterval=501ms",
        "zeebe.gateway.cluster.membership.gossipFanout=5",
        "zeebe.gateway.cluster.membership.probeInterval=2001ms",
        "zeebe.gateway.cluster.membership.probeTimeout=201ms",
        "zeebe.gateway.cluster.membership.suspectProbes=6",
        "zeebe.gateway.cluster.membership.failureTimeout=2001ms",
        "zeebe.gateway.cluster.membership.syncInterval=21001ms",
        // legacy broker configuration
        "zeebe.broker.cluster.membership.broadcastUpdates=false",
        "zeebe.broker.cluster.membership.broadcastDisputes=true",
        "zeebe.broker.cluster.membership.notifySuspect=false",
        "zeebe.broker.cluster.membership.gossipInterval=502ms",
        "zeebe.broker.cluster.membership.gossipFanout=7",
        "zeebe.broker.cluster.membership.probeInterval=2002ms",
        "zeebe.broker.cluster.membership.probeTimeout=202ms",
        "zeebe.broker.cluster.membership.suspectProbes=8",
        "zeebe.broker.cluster.membership.failureTimeout=20002ms",
        "zeebe.broker.cluster.membership.syncInterval=21002ms"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetMembershipFromNew() {
      assertThat(brokerCfg.getCluster().getMembership())
          .returns(true, MembershipCfg::isBroadcastUpdates)
          .returns(false, MembershipCfg::isBroadcastDisputes)
          .returns(true, MembershipCfg::isNotifySuspect)
          .returns(Duration.ofMillis(500), MembershipCfg::getGossipInterval)
          .returns(4, MembershipCfg::getGossipFanout)
          .returns(Duration.ofMillis(2000), MembershipCfg::getProbeInterval)
          .returns(Duration.ofMillis(200), MembershipCfg::getProbeTimeout)
          .returns(5, MembershipCfg::getSuspectProbes)
          .returns(Duration.ofMillis(20000), MembershipCfg::getFailureTimeout)
          .returns(Duration.ofMillis(21000), MembershipCfg::getSyncInterval);
    }
  }
}
