/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.netty.util.NetUtil;
import java.io.File;
import java.net.URL;
import java.security.cert.CertificateException;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public final class SecurityTest {
  public final Timeout testTimeout = Timeout.seconds(120);

  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1, 1, 1, brokerCfg -> {}, this::configureGatewayForTls, this::configureClientForTls);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(testTimeout).around(clusteringRule);

  @Test
  public void shouldEstablishSecureConnection() {
    final var client = newSecureClient().build();

    // when
    final Topology topology = client.newTopologyRequest().send().join();

    // then
    assertThat(topology.getBrokers().size()).isEqualTo(1);
  }

  @Test
  public void shouldAllowToOverrideAuthority() {
    final var client = newSecureClient().overrideAuthority("localhost").build();

    // when
    final Topology topology = client.newTopologyRequest().send().join();

    // then
    assertThat(topology.getBrokers().size()).isEqualTo(1);
  }

  @Test
  public void shouldRejectDifferentAuthority() {
    // given
    final var client = newSecureClient().overrideAuthority("virtualhost").build();

    // when, then
    Assertions.assertThatThrownBy(() -> client.newTopologyRequest().send().join())
        .hasRootCauseInstanceOf(CertificateException.class)
        .hasRootCauseMessage("No name matching virtualhost found");
  }

  private ZeebeClientBuilder newSecureClient() {
    return configureClientForTls(ZeebeClient.newClientBuilder())
        .gatewayAddress(NetUtil.toSocketAddressString(clusteringRule.getGatewayAddress()));
  }

  private ZeebeClientBuilder configureClientForTls(final ZeebeClientBuilder clientBuilder) {
    return clientBuilder.caCertificatePath(getResource("security/test-chain.cert.pem").getPath());
  }

  private void configureGatewayForTls(final GatewayCfg gatewayCfg) {
    final String certificatePath = getResource("security/test-chain.cert.pem").getFile();
    final String privateKeyPath = getResource("security/test-server.key.pem").getFile();

    gatewayCfg
        .getSecurity()
        .setEnabled(true)
        .setCertificateChainPath(new File(certificatePath))
        .setPrivateKeyPath(new File(privateKeyPath));
  }

  private URL getResource(final String name) {
    return getClass().getClassLoader().getResource(name);
  }
}
