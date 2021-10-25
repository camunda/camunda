/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.security;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.AtomixCluster;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SecurityTest {
  private SelfSignedCertificate certificate;
  private Gateway gateway;

  @BeforeEach
  void beforeEach() throws Exception {
    certificate = new SelfSignedCertificate();
  }

  @AfterEach
  public void tearDown() {
    if (gateway != null) {
      gateway.stop();
      gateway = null;
    }
  }

  @Test
  void shouldStartWithTlsEnabled() throws IOException {
    // given
    final GatewayCfg cfg = createGatewayCfg();

    // when
    gateway = buildGateway(cfg);
    gateway.start();

    // then
    SslAssert.assertThat(cfg.getNetwork().toSocketAddress()).isSecuredBy(certificate);
  }

  @Test
  void shouldNotStartWithTlsEnabledAndWrongCert() {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getSecurity().setCertificateChainPath(new File("/tmp/i-dont-exist.crt"));

    // when
    gateway = buildGateway(cfg);

    // then
    assertThatCode(() -> gateway.start())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to find a certificate chain file at the provided location '%s' but none was found.",
            cfg.getSecurity().getCertificateChainPath());
  }

  @Test
  void shouldNotStartWithTlsEnabledAndWrongKey() {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getSecurity().setPrivateKeyPath(new File("/tmp/i-dont-exist.key"));

    // when
    gateway = buildGateway(cfg);

    // then
    assertThatCode(() -> gateway.start())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to find a private key file at the provided location '%s' but none was found.",
            cfg.getSecurity().getPrivateKeyPath());
  }

  @Test
  void shouldNotStartWithTlsEnabledAndNoPrivateKey() {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getSecurity().setPrivateKeyPath(null);

    // when
    gateway = buildGateway(cfg);

    // then
    assertThatCode(() -> gateway.start())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to find a valid path to a private key but none was found. "
                + "Edit the gateway configuration file to provide one or to disable TLS.");
  }

  @Test
  void shouldNotStartWithTlsEnabledAndNoCert() {
    // given
    final GatewayCfg cfg = createGatewayCfg();
    cfg.getSecurity().setCertificateChainPath(null);

    // when
    gateway = buildGateway(cfg);

    // then
    assertThatCode(() -> gateway.start())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected to find a valid path to a certificate chain but none was found. "
                + "Edit the gateway configuration file to provide one or to disable TLS.");
  }

  private GatewayCfg createGatewayCfg() {
    final var gatewayAddress = SocketUtil.getNextAddress();
    return new GatewayCfg()
        .setNetwork(
            new NetworkCfg()
                .setHost(gatewayAddress.getHostName())
                .setPort(gatewayAddress.getPort()))
        .setSecurity(
            new SecurityCfg()
                .setEnabled(true)
                .setCertificateChainPath(certificate.certificate())
                .setPrivateKeyPath(certificate.privateKey()));
  }

  private Gateway buildGateway(final GatewayCfg gatewayCfg) {
    final var clusterAddress = SocketUtil.getNextAddress();
    final var atomix =
        AtomixCluster.builder()
            .withAddress(Address.from(clusterAddress.getHostName(), clusterAddress.getPort()))
            .build();
    final var actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();

    return new Gateway(
        gatewayCfg,
        atomix.getMessagingService(),
        atomix.getMembershipService(),
        atomix.getEventService(),
        actorScheduler);
  }
}
