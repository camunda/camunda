/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.NodeConfig;
import io.atomix.cluster.discovery.BootstrapDiscoveryConfig;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.protocol.SwimMembershipProtocolConfig;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.MembershipCfg;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class GatewayClusterConfiguration {

  private MeterRegistry meterRegistry;

  @Bean
  public ClusterConfig clusterConfig(
      final GatewayConfiguration gatewayConfig, final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    final var config = gatewayConfig.config();
    final var cluster = config.getCluster();
    final var name = cluster.getClusterName();
    final var messaging = messagingConfig(config);
    final var discovery = discoveryConfig(cluster.getInitialContactPoints());
    final var membership = membershipConfig(cluster.getMembership());
    final var member = memberConfig(cluster);

    return new ClusterConfig()
        .setClusterId(name)
        .setNodeConfig(member)
        .setDiscoveryConfig(discovery)
        .setMessagingConfig(messaging)
        .setProtocolConfig(membership);
  }

  @Bean(destroyMethod = "stop")
  public AtomixCluster atomixCluster(final ClusterConfig config) {
    return new AtomixCluster(config, Version.from(VersionUtil.getVersion()), meterRegistry);
  }

  private MemberConfig memberConfig(final ClusterCfg cluster) {
    final var advertisedAddress =
        Address.from(cluster.getAdvertisedHost(), cluster.getAdvertisedPort());

    return new MemberConfig().setId(cluster.getMemberId()).setAddress(advertisedAddress);
  }

  private SwimMembershipProtocolConfig membershipConfig(final MembershipCfg config) {
    return new SwimMembershipProtocolConfig()
        .setBroadcastDisputes(config.isBroadcastDisputes())
        .setBroadcastUpdates(config.isBroadcastUpdates())
        .setFailureTimeout(config.getFailureTimeout())
        .setGossipFanout(config.getGossipFanout())
        .setGossipInterval(config.getGossipInterval())
        .setNotifySuspect(config.isNotifySuspect())
        .setProbeInterval(config.getProbeInterval())
        .setProbeTimeout(config.getProbeTimeout())
        .setSuspectProbes(config.getSuspectProbes())
        .setSyncInterval(config.getSyncInterval());
  }

  private BootstrapDiscoveryConfig discoveryConfig(final Collection<String> contactPoints) {
    final var nodes =
        contactPoints.stream()
            .map(Address::from)
            .map(address -> new NodeConfig().setAddress(address))
            .collect(Collectors.toSet());
    return new BootstrapDiscoveryConfig().setNodes(nodes);
  }

  private MessagingConfig messagingConfig(final GatewayCfg config) {
    final var cluster = config.getCluster();

    final var messaging =
        new MessagingConfig()
            .setCompressionAlgorithm(cluster.getMessageCompression())
            .setInterfaces(Collections.singletonList(cluster.getHost()))
            .setPort(cluster.getPort());

    final var security = cluster.getSecurity();
    if (security.isEnabled()) {
      messaging
          .setTlsEnabled(true)
          .setCertificateChain(security.getCertificateChainPath())
          .setPrivateKey(security.getPrivateKeyPath());
    }
    return messaging;
  }
}
