/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.actor.ActorClockConfiguration;
import io.camunda.application.commons.actor.ActorIdleStrategyConfiguration.IdleStrategySupplier;
import io.camunda.application.commons.actor.ActorSchedulerConfiguration;
import io.camunda.application.commons.broker.client.BrokerClientConfiguration;
import io.camunda.application.commons.clustering.AtomixClusterConfiguration;
import io.camunda.application.commons.clustering.DynamicClusterServices;
import io.camunda.application.commons.configuration.GatewayBasedConfiguration;
import io.camunda.application.commons.configuration.GatewayBasedConfiguration.GatewayBasedProperties;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.net.InetSocketAddress;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;

final class StandaloneGatewaySecurityTest {
  private SelfSignedCertificate certificate;
  private GatewayModuleConfiguration gateway;
  private BrokerClient brokerClient;
  private AtomixCluster atomixCluster;
  private ActorScheduler actorScheduler;
  private JobStreamClient jobStreamClient;

  @BeforeEach
  void beforeEach() throws Exception {
    certificate = new SelfSignedCertificate();
  }

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(
        gateway, brokerClient, jobStreamClient, actorScheduler, () -> atomixCluster.stop().join());
  }

  @Test
  void shouldStartWithTlsEnabled() throws Exception {
    // given
    final var cfg = createGatewayCfg();

    // when
    gateway = buildGateway(cfg);
    gateway.gateway();

    // then
    final var clusterAddress =
        new InetSocketAddress(cfg.getCluster().getHost(), cfg.getCluster().getPort());
    SslAssert.assertThat(clusterAddress).isSecuredBy(certificate);
  }

  @Test
  void shouldNotStartWithTlsEnabledAndWrongCert() {
    // given
    final var cfg = createGatewayCfg();
    cfg.getCluster().getSecurity().setCertificateChainPath(new File("/tmp/i-dont-exist.crt"));

    // when - then
    assertThatCode(() -> buildGateway(cfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the node's inter-cluster communication certificate to be at /tmp/i-dont-exist.crt, but either the file is missing or it is not readable");
  }

  @Test
  void shouldNotStartWithTlsEnabledAndWrongKey() {
    // given
    final var cfg = createGatewayCfg();
    cfg.getCluster().getSecurity().setPrivateKeyPath(new File("/tmp/i-dont-exist.key"));

    // when - then
    assertThatCode(() -> buildGateway(cfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the node's inter-cluster communication private key to be at /tmp/i-dont-exist.key, but either the file is missing or it is not readable");
  }

  @Test
  void shouldNotStartWithTlsEnabledAndNoPrivateKey() {
    // given
    final var cfg = createGatewayCfg();
    cfg.getCluster().getSecurity().setPrivateKeyPath(null);

    // when - then
    assertThatCode(() -> buildGateway(cfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected a private key in order to enable inter-cluster communication security, but none given");
  }

  @Test
  void shouldNotStartWithTlsEnabledAndNoCert() {
    // given
    final var cfg = createGatewayCfg();
    cfg.getCluster().getSecurity().setCertificateChainPath(null);

    // when - then
    assertThatCode(() -> buildGateway(cfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected a certificate chain in order to enable inter-cluster communication security, but none given");
  }

  private GatewayBasedProperties createGatewayCfg() {
    final var gatewayAddress = SocketUtil.getNextAddress();
    final var clusterAddress = SocketUtil.getNextAddress();
    final var config = new GatewayBasedProperties();
    config.setNetwork(
        new NetworkCfg().setHost(gatewayAddress.getHostName()).setPort(gatewayAddress.getPort()));
    config.setCluster(
        new ClusterCfg()
            .setHost(clusterAddress.getHostName())
            .setPort(clusterAddress.getPort())
            .setSecurity(
                new SecurityCfg()
                    .setEnabled(true)
                    .setCertificateChainPath(certificate.certificate())
                    .setPrivateKeyPath(certificate.privateKey())));
    return config;
  }

  private GatewayModuleConfiguration buildGateway(final GatewayBasedProperties gatewayCfg) {
    final var gatewayConfig = new GatewayBasedConfiguration(gatewayCfg, new LifecycleProperties());
    final var schedulerConfig = gatewayConfig.schedulerConfiguration();
    final var brokerClientConfig = gatewayConfig.brokerClientConfig();

    final var clusterConfig = gatewayConfig.clusterConfig();
    final var clusterConfiguration = new AtomixClusterConfiguration(clusterConfig);
    atomixCluster = clusterConfiguration.atomixCluster();
    final ActorSchedulerConfiguration actorSchedulerConfiguration =
        new ActorSchedulerConfiguration(
            schedulerConfig, IdleStrategySupplier.ofDefault(), new ActorClockConfiguration(false));

    actorScheduler = actorSchedulerConfiguration.scheduler();
    final var topologyServices = new DynamicClusterServices(actorScheduler, atomixCluster);
    final var topologyManager = topologyServices.brokerTopologyManager();
    topologyServices.gatewayClusterTopologyService(topologyManager);

    final var brokerClientConfiguration =
        new BrokerClientConfiguration(
            brokerClientConfig, atomixCluster, actorScheduler, topologyManager);
    brokerClient = brokerClientConfiguration.brokerClient();
    jobStreamClient = new JobStreamComponent().jobStreamClient(actorScheduler, atomixCluster);

    return new GatewayModuleConfiguration(
        gatewayConfig,
        null, // identity is disabled by default
        new SpringGatewayBridge(),
        actorScheduler,
        atomixCluster,
        brokerClient,
        jobStreamClient);
  }
}
