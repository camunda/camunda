/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.AtomixClusterBuilder;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.protocol.GroupMembershipProtocol;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.MembershipCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.ApplicationScope;

@Component
final class AtomixComponent {
  private final GatewayCfg config;

  @Autowired
  AtomixComponent(final GatewayCfg config) {
    this.config = config;
  }

  @Bean("atomixCluster")
  @ApplicationScope(proxyMode = ScopedProxyMode.NO)
  AtomixCluster createAtomixCluster() {
    final var clusterConfig = config.getCluster();
    final var membershipProtocol = createMembershipProtocol(clusterConfig.getMembership());

    final var builder =
        AtomixCluster.builder()
            .withMemberId(clusterConfig.getMemberId())
            .withMessagingInterface(clusterConfig.getHost())
            .withMessagingPort(clusterConfig.getPort())
            .withAddress(
                Address.from(clusterConfig.getAdvertisedHost(), clusterConfig.getAdvertisedPort()))
            .withClusterId(clusterConfig.getClusterName())
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder()
                    .withNodes(
                        clusterConfig.getInitialContactPoints().stream()
                            .map(Address::from)
                            .toArray(Address[]::new))
                    .build())
            .withMembershipProtocol(membershipProtocol)
            .withMessageCompression(clusterConfig.getMessageCompression());

    if (clusterConfig.getSecurity().isEnabled()) {
      applyClusterSecurityConfig(clusterConfig, builder);
    }

    return builder.build();
  }

  private GroupMembershipProtocol createMembershipProtocol(final MembershipCfg config) {
    return SwimMembershipProtocol.builder()
        .withFailureTimeout(config.getFailureTimeout())
        .withGossipInterval(config.getGossipInterval())
        .withProbeInterval(config.getProbeInterval())
        .withProbeTimeout(config.getProbeTimeout())
        .withBroadcastDisputes(config.isBroadcastDisputes())
        .withBroadcastUpdates(config.isBroadcastUpdates())
        .withGossipFanout(config.getGossipFanout())
        .withNotifySuspect(config.isNotifySuspect())
        .withSuspectProbes(config.getSuspectProbes())
        .withSyncInterval(config.getSyncInterval())
        .build();
  }

  private void applyClusterSecurityConfig(
      final ClusterCfg config, final AtomixClusterBuilder builder) {
    final var security = config.getSecurity();
    final var certificateChainPath = security.getCertificateChainPath();
    final var privateKeyPath = security.getPrivateKeyPath();

    if (certificateChainPath == null) {
      throw new IllegalArgumentException(
          "Expected to have a valid certificate chain path for cluster security, but none "
              + "configured");
    }

    if (privateKeyPath == null) {
      throw new IllegalArgumentException(
          "Expected to have a valid private key path for cluster security, but none was "
              + "configured");
    }

    if (!certificateChainPath.canRead()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the configured cluster security certificate chain path '%s' to point to a"
                  + " readable file, but it does not",
              certificateChainPath));
    }

    if (!privateKeyPath.canRead()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the configured cluster security private key path '%s' to point to a "
                  + "readable file, but it does not",
              privateKeyPath));
    }

    builder.withSecurity(certificateChainPath, privateKeyPath);
  }
}
