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
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.shared.ActorClockConfiguration;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.net.InetSocketAddress;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    final GatewayCfg cfg = createGatewayCfg();

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
    final GatewayCfg cfg = createGatewayCfg();
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
    final GatewayCfg cfg = createGatewayCfg();
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
    final GatewayCfg cfg = createGatewayCfg();
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
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getCluster().getSecurity().setCertificateChainPath(null);

    // when - then
    assertThatCode(() -> buildGateway(cfg))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected a certificate chain in order to enable inter-cluster communication security, but none given");
  }

  private GatewayCfg createGatewayCfg() {
    final var gatewayAddress = SocketUtil.getNextAddress();
    final var clusterAddress = SocketUtil.getNextAddress();
    return new GatewayCfg()
        .setNetwork(
            new NetworkCfg()
                .setHost(gatewayAddress.getHostName())
                .setPort(gatewayAddress.getPort()))
        .setCluster(
            new ClusterCfg()
                .setHost(clusterAddress.getHostName())
                .setPort(clusterAddress.getPort())
                .setSecurity(
                    new SecurityCfg()
                        .setEnabled(true)
                        .setCertificateChainPath(certificate.certificate())
                        .setPrivateKeyPath(certificate.privateKey())));
  }

  private StandaloneGateway buildGateway(final GatewayCfg gatewayCfg) {
    atomixCluster = new GatewayClusterConfiguration().atomixCluster(gatewayCfg);
    final ActorSchedulerComponent actorSchedulerComponent =
        new ActorSchedulerComponent(gatewayCfg, new ActorClockConfiguration(false));
    actorScheduler = actorSchedulerComponent.actorScheduler();
    final BrokerClientComponent brokerClientComponent =
        new BrokerClientComponent(gatewayCfg, atomixCluster, actorScheduler);
    brokerClient = brokerClientComponent.brokerClient();
    jobStreamClient = new JobStreamComponent().jobStreamClient(actorScheduler, atomixCluster);

    return new StandaloneGateway(
        gatewayCfg,
        new SpringGatewayBridge(),
        actorScheduler,
        atomixCluster,
        brokerClient,
        jobStreamClient);
  }
}
