/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
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

  @BeforeEach
  void beforeEach() throws Exception {
    certificate = new SelfSignedCertificate();
  }

  @AfterEach
  public void tearDown() {
    CloseHelper.quietClose(gateway);
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

    // when
    gateway = buildGateway(cfg);

    // then
    assertThatCode(() -> gateway.run())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the configured cluster security certificate chain path "
                + "'/tmp/i-dont-exist.crt' to point to a readable file, but it does not");
  }

  @Test
  void shouldNotStartWithTlsEnabledAndWrongKey() {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getCluster().getSecurity().setPrivateKeyPath(new File("/tmp/i-dont-exist.key"));

    // when
    gateway = buildGateway(cfg);

    // then
    assertThatCode(() -> gateway.run())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected the configured cluster security private key path '/tmp/i-dont-exist.key' to "
                + "point to a readable file, but it does not");
  }

  @Test
  void shouldNotStartWithTlsEnabledAndNoPrivateKey() {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getCluster().getSecurity().setPrivateKeyPath(null);

    // when
    gateway = buildGateway(cfg);

    // then
    assertThatCode(() -> gateway.run())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have a valid private key path for cluster security, but none was configured");
  }

  @Test
  void shouldNotStartWithTlsEnabledAndNoCert() {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getCluster().getSecurity().setCertificateChainPath(null);

    // when
    gateway = buildGateway(cfg);

    // then
    assertThatCode(() -> gateway.run())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to have a valid certificate chain path for cluster security, but none "
                + "configured");
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
    return new StandaloneGateway(
        gatewayCfg, new SpringGatewayBridge(), new ActorClockConfiguration(false));
  }
}
