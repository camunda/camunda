/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.network;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class SecureClusteredMessagingIT {

  public static final String AUTHORITY = "localhost";
  private final SelfSignedCertificate certificate = newCertificate();

  @RegisterExtension
  private final ClusteringRuleExtension cluster =
      new ClusteringRuleExtension(1, 2, 2, this::configureBroker, this::configureGateway);

  @Test
  void shouldFormAClusterWithTls() {
    // given - a cluster with 2 standalone brokers, and 1 standalone gateway

    // when - note the client is using plaintext since we only care about inter-cluster TLS
    final Topology topology =
        cluster.getClient().newTopologyRequest().send().join(15, TimeUnit.SECONDS);

    // then - ensure the cluster is formed correctly and all inter-cluster communication endpoints
    // are secured using the expected certificate
    TopologyAssert.assertThat(topology).hasBrokersCount(2).isComplete(2, 1, 2);
    cluster.getBrokers().forEach(this::assertBrokerMessagingServicesAreSecured);
    assertAddressIsSecured("gateway", getGatewayAddress());
  }

  /** Verifies that both the command and internal APIs of the broker are correctly secured. */
  private void assertBrokerMessagingServicesAreSecured(final Broker broker) {
    final var commandApiAddress =
        broker.getConfig().getNetwork().getCommandApi().getAdvertisedAddress();
    final var internalApiAddress =
        broker.getConfig().getNetwork().getInternalApi().getAdvertisedAddress();

    assertAddressIsSecured(broker.getConfig().getCluster().getNodeId(), commandApiAddress);
    assertAddressIsSecured(broker.getConfig().getCluster().getNodeId(), internalApiAddress);
  }

  private InetSocketAddress getGatewayAddress() {
    final ClusterCfg clusterConfig = cluster.getGateway().getGatewayCfg().getCluster();
    final var address =
        Address.from(clusterConfig.getAdvertisedHost(), clusterConfig.getAdvertisedPort());
    return address.socketAddress();
  }

  private void assertAddressIsSecured(final Object nodeId, final SocketAddress address) {
    SslAssert.assertThat(address)
        .as("node %s is not secured correctly at address %s", nodeId, address)
        .isSecuredBy(certificate);
  }

  private SelfSignedCertificate newCertificate() {
    try {
      return new SelfSignedCertificate(AUTHORITY);
    } catch (final CertificateException e) {
      throw new IllegalStateException("Failed to create self-signed certificate", e);
    }
  }

  private void configureGateway(final GatewayCfg config) {
    config.getCluster().getSecurity().setEnabled(true);
    config.getCluster().getSecurity().setCertificateChainPath(certificate.certificate());
    config.getCluster().getSecurity().setPrivateKeyPath(certificate.privateKey());
    config.getCluster().getSecurity().setOverrideAuthority(AUTHORITY);
  }

  private void configureBroker(final BrokerCfg config) {
    config.getNetwork().getSecurity().setEnabled(true);
    config.getNetwork().getSecurity().setCertificateChainPath(certificate.certificate());
    config.getNetwork().getSecurity().setPrivateKeyPath(certificate.privateKey());
    config.getNetwork().getSecurity().setOverrideAuthority(AUTHORITY);
  }
}
