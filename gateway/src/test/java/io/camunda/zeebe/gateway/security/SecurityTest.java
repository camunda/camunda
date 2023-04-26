/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.AtomixCluster;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClientImpl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.IOException;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SecurityTest {
  private SelfSignedCertificate certificate;
  private Gateway gateway;
  private ActorScheduler actorScheduler;
  private AtomixCluster atomix;
  private BrokerClient brokerClient;
  private JobStreamClient jobStreamClient;

  @BeforeEach
  void beforeEach() throws Exception {
    certificate = new SelfSignedCertificate();
  }

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(
        gateway, brokerClient, jobStreamClient, actorScheduler, () -> atomix.stop().join());
  }

  @Test
  void shouldStartWithTlsEnabled() throws IOException {
    // given
    final GatewayCfg cfg = createGatewayCfg();

    // when
    gateway = buildGateway(cfg);
    gateway.start().join();

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
    assertThatThrownBy(() -> gateway.start().join())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
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
    assertThatThrownBy(() -> gateway.start().join())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
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
    assertThatThrownBy(() -> gateway.start().join())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
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
    assertThatThrownBy(() -> gateway.start().join())
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasRootCauseMessage(
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
    atomix =
        AtomixCluster.builder()
            .withAddress(Address.from(clusterAddress.getHostName(), clusterAddress.getPort()))
            .build();
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
    brokerClient =
        new BrokerClientImpl(
            gatewayCfg.getCluster().getRequestTimeout(),
            atomix.getMessagingService(),
            atomix.getMembershipService(),
            atomix.getEventService(),
            actorScheduler);
    jobStreamClient = new JobStreamClientImpl(actorScheduler, atomix.getCommunicationService());
    jobStreamClient.start();

    // before we can add the job stream client as a topology listener, we need to wait for the
    // topology to be set up, otherwise the callback may be lost
    brokerClient.start().forEach(ActorFuture::join);
    brokerClient.getTopologyManager().addTopologyListener(jobStreamClient);
    return new Gateway(gatewayCfg, brokerClient, actorScheduler, jobStreamClient.streamer());
  }
}
