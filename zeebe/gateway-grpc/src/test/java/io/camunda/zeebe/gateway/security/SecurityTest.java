/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.AtomixCluster;
import io.atomix.utils.net.Address;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClientImpl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.IOException;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

final class SecurityTest {
  private SelfSignedCertificate certificate;
  private Gateway gateway;
  private ActorScheduler actorScheduler;
  private AtomixCluster atomix;
  private BrokerClient brokerClient;
  private JobStreamClient jobStreamClient;
  private BrokerTopologyManagerImpl topologyManager;
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @BeforeEach
  void beforeEach() throws Exception {
    certificate = new SelfSignedCertificate();
  }

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(
        gateway,
        brokerClient,
        topologyManager,
        jobStreamClient,
        actorScheduler,
        () -> atomix.stop().join());
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
            "Expected the configured network security certificate chain path '%s' to point to a"
                + " readable file, but it does not",
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
            "Expected the configured network security private key path '%s' to point to a "
                + "readable file, but it does not",
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
            "Expected to have a valid private key path for network security, but none configured");
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
            "Expected to have a valid certificate chain path for network security, but none configured");
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
        AtomixCluster.builder(meterRegistry)
            .withAddress(Address.from(clusterAddress.getHostName(), clusterAddress.getPort()))
            .build();
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
    topologyManager =
        new BrokerTopologyManagerImpl(
            () -> atomix.getMembershipService().getMembers(),
            new BrokerClientTopologyMetrics(meterRegistry));
    actorScheduler.submitActor(topologyManager).join();

    brokerClient =
        new BrokerClientImpl(
            gatewayCfg.getCluster().getRequestTimeout(),
            atomix.getMessagingService(),
            atomix.getEventService(),
            actorScheduler,
            topologyManager,
            new BrokerClientRequestMetrics(meterRegistry));
    jobStreamClient =
        new JobStreamClientImpl(actorScheduler, atomix.getCommunicationService(), meterRegistry);
    jobStreamClient.start().join();

    // before we can add the job stream client as a topology listener, we need to wait for the
    // topology to be set up, otherwise the callback may be lost
    brokerClient.start().forEach(ActorFuture::join);
    topologyManager.addTopologyListener(jobStreamClient);
    atomix.getMembershipService().addListener(topologyManager);
    return new Gateway(
        gatewayCfg,
        new SecurityConfiguration(),
        brokerClient,
        actorScheduler,
        jobStreamClient.streamer(),
        mock(UserServices.class),
        mock(PasswordEncoder.class),
        meterRegistry,
        mock(JwtDecoder.class));
  }
}
