/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.GatewayConfiguration.GatewayProperties;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.shared.ActorClockConfiguration;
import io.camunda.zeebe.shared.IdleStrategyConfig.IdleStrategySupplier;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
  private StandaloneGateway gateway;
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
    gateway.run();

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

  private GatewayProperties createGatewayCfg() {
    final var gatewayAddress = SocketUtil.getNextAddress();
    final var clusterAddress = SocketUtil.getNextAddress();
    final var config = new GatewayProperties();
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

  private StandaloneGateway buildGateway(final GatewayProperties gatewayCfg) {
    final var meterRegistry = new SimpleMeterRegistry();
    final var config = new GatewayConfiguration(gatewayCfg, new LifecycleProperties());
    final var clusterConfig = new GatewayClusterConfiguration();
    atomixCluster =
        new GatewayClusterConfiguration().atomixCluster(clusterConfig.clusterConfig(config));
    final ActorSchedulerConfiguration actorSchedulerConfiguration =
        new ActorSchedulerConfiguration(gatewayCfg, new ActorClockConfiguration(false));
    actorScheduler = actorSchedulerConfiguration.actorScheduler(IdleStrategySupplier.ofDefault());
    final var topologyServices = new TopologyServices(actorScheduler, atomixCluster, meterRegistry);
    final var clusterTopologyService = topologyServices.gatewayClusterTopologyService();
    final var topologyManager = topologyServices.brokerTopologyManager(clusterTopologyService);

    final BrokerClientComponent brokerClientComponent =
        new BrokerClientComponent(
            config, atomixCluster, actorScheduler, topologyManager, meterRegistry);
    brokerClient = brokerClientComponent.brokerClient();
    jobStreamClient = new JobStreamComponent().jobStreamClient(actorScheduler, atomixCluster);

    return new StandaloneGateway(
        config,
        null, // identity is disabled by default
        new SpringGatewayBridge(),
        actorScheduler,
        atomixCluster,
        brokerClient,
        jobStreamClient,
        meterRegistry);
  }
}
