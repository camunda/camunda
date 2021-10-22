/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.it.clustering.DeploymentClusteredTest;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.netty.util.NetUtil;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public final class SecurityTest {
  public final Timeout testTimeout = Timeout.seconds(120);

  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1, 1, 1, brokerCfg -> {}, this::configureGatewayForTls, this::configureClientForTls);

  public final GrpcClientRule clientRule =
      new GrpcClientRule(
          cfg ->
              configureClientForTls(
                  cfg.gatewayAddress(
                      NetUtil.toSocketAddressString(clusteringRule.getGatewayAddress()))));

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldEstablishSecureConnection() {
    // when
    final Topology topology = clientRule.getClient().newTopologyRequest().send().join();

    // then
    assertThat(topology.getBrokers().size()).isEqualTo(1);
  }

  private ZeebeClientBuilder configureClientForTls(final ZeebeClientBuilder clientBuilder) {
    return clientBuilder.caCertificatePath(
        DeploymentClusteredTest.class
            .getClassLoader()
            .getResource("security/test-chain.cert.pem")
            .getPath());
  }

  private void configureGatewayForTls(final GatewayCfg gatewayCfg) {
    final String certificatePath =
        getClass().getClassLoader().getResource("security/test-chain.cert.pem").getFile();
    final String privateKeyPath =
        getClass().getClassLoader().getResource("security/test-server.key.pem").getFile();

    gatewayCfg
        .getSecurity()
        .setEnabled(true)
        .setCertificateChainPath(new File(certificatePath))
        .setPrivateKeyPath(new File(privateKeyPath));
  }
}
