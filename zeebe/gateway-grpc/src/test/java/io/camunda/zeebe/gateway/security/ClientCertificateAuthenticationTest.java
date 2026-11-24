/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.AtomixCluster;
import io.atomix.utils.net.Address;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.api.util.GatewayTestFactory;
import io.camunda.zeebe.gateway.impl.configuration.ClientAuthCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClientImpl;
import io.camunda.zeebe.gateway.security.TestPki.Identity;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.tls.ClientCertificateMode;
import io.grpc.ManagedChannel;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Verifies the gRPC gateway's mutual TLS: which client certificates pass the handshake depending on
 * the configured mode, trusted CAs and allowed identities. A client counts as connected when a
 * health check reaches the server, whatever its answer; a TLS rejection surfaces as UNAVAILABLE.
 */
final class ClientCertificateAuthenticationTest {
  private static final String ALLOWED_SAN = "worker.example.com";

  @TempDir private Path tempDir;
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private Identity serverCa;
  private Identity clientCa;
  private Identity otherClientCa;
  private Gateway gateway;
  private ActorScheduler actorScheduler;
  private AtomixCluster atomix;
  private BrokerClient brokerClient;
  private JobStreamClient jobStreamClient;
  private BrokerTopologyManagerImpl topologyManager;
  private GatewayCfg cfg;

  @BeforeEach
  void beforeEach() throws Exception {
    serverCa = TestPki.newCa("server-ca");
    clientCa = TestPki.newCa("client-ca");
    otherClientCa = TestPki.newCa("other-client-ca");
    cfg = createGatewayCfg();
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(
        gateway,
        brokerClient,
        topologyManager,
        jobStreamClient,
        actorScheduler,
        () -> atomix.stop().join());
  }

  @Test
  void shouldAcceptClientWithAllowedSan() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);
    final var client = TestPki.newLeaf(clientCa, "worker", ALLOWED_SAN);

    // when
    startGateway();

    // then
    assertThat(connects(client)).isTrue();
    assertThat(acceptedCount(ALLOWED_SAN)).isEqualTo(1);
  }

  @Test
  void shouldRejectClientWithoutCertificate() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);

    // when
    startGateway();

    // then
    assertThat(connects(null)).isFalse();
  }

  @Test
  void shouldRejectClientSignedByUntrustedCa() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);
    final var client = TestPki.newLeaf(otherClientCa, "worker", ALLOWED_SAN);

    // when
    startGateway();

    // then
    // the client is never asked for a certificate from its CA, so it presents none: the
    // handshake fails without the trust manager seeing, and thus without being counted
    assertThat(connects(client)).isFalse();
  }

  @Test
  void shouldRejectExpiredClientCertificate() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);
    final var client =
        TestPki.newLeaf(clientCa, "worker", Instant.now().minus(Duration.ofHours(1)), ALLOWED_SAN);

    // when
    startGateway();

    // then
    assertThat(connects(client)).isFalse();
    assertThat(rejectedCount("EXPIRED", true)).isEqualTo(1);
  }

  @Test
  void shouldRejectClientWithSanNotAllowed() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);
    final var client = TestPki.newLeaf(clientCa, "intruder", "intruder.example.com");

    // when
    startGateway();

    // then
    assertThat(connects(client)).isFalse();
    assertThat(rejectedCount("IDENTITY_NOT_ALLOWED", true)).isEqualTo(1);
  }

  @Test
  void shouldRejectClientMatchingOnlyByCommonNameWithoutFallback() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);
    final var client = TestPki.newLeaf(clientCa, ALLOWED_SAN);

    // when
    startGateway();

    // then
    assertThat(connects(client)).isFalse();
  }

  @Test
  void shouldAcceptClientMatchingByCommonNameWithFallback() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);
    cfg.getSecurity().getClientAuth().setCommonNameFallback(true);
    final var client = TestPki.newLeaf(clientCa, ALLOWED_SAN);

    // when
    startGateway();

    // then
    assertThat(connects(client)).isTrue();
  }

  @Test
  void shouldAcceptClientsOfEveryCaInBundle() throws Exception {
    // given - a CA rotation in progress: both the old and the new CA are trusted
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa, otherClientCa);
    final var oldClient = TestPki.newLeaf(clientCa, "worker", ALLOWED_SAN);
    final var newClient = TestPki.newLeaf(otherClientCa, "worker", ALLOWED_SAN);

    // when
    startGateway();

    // then
    assertThat(connects(oldClient)).isTrue();
    assertThat(connects(newClient)).isTrue();
  }

  @Test
  void shouldAcceptAnyTrustedClientWithoutAllowList() throws Exception {
    // given
    requireClientCertificates(List.of(), clientCa);
    final var client = TestPki.newLeaf(clientCa, "anyone", "anyone.example.com");

    // when
    startGateway();

    // then
    assertThat(connects(client)).isTrue();
  }

  @Test
  void shouldAcceptClientWithoutCertificateWhenOptional() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);
    cfg.getSecurity().getClientAuth().setMode(ClientCertificateMode.OPTIONAL);

    // when
    startGateway();

    // then
    assertThat(connects(null)).isTrue();
  }

  @Test
  void shouldAcceptButReportDisallowedClientWhenOptional() throws Exception {
    // given
    requireClientCertificates(List.of(ALLOWED_SAN), clientCa);
    cfg.getSecurity().getClientAuth().setMode(ClientCertificateMode.OPTIONAL);
    final var client = TestPki.newLeaf(otherClientCa, "intruder", "intruder.example.com");

    // when
    startGateway();

    // then
    assertThat(connects(client)).isTrue();
    assertThat(rejectedCount("UNTRUSTED", false)).isEqualTo(1);
  }

  @Test
  void shouldAcceptClientWithoutCertificateByDefault() throws Exception {
    // given - no client auth configured

    // when
    startGateway();

    // then
    assertThat(connects(null)).isTrue();
  }

  private void requireClientCertificates(
      final List<String> allowedIdentities, final Identity... trustedCas) throws Exception {
    cfg.getSecurity()
        .setClientAuth(
            new ClientAuthCfg()
                .setMode(ClientCertificateMode.REQUIRED)
                .setTrustedCertificatesPath(
                    TestPki.writeBundle(tempDir.resolve("client-cas.pem").toFile(), trustedCas))
                .setAllowedIdentities(allowedIdentities));
  }

  private boolean connects(final @Nullable Identity client) throws Exception {
    final var sslContext = GrpcSslContexts.forClient().trustManager(serverCa.certificate());
    if (client != null) {
      sslContext.keyManager(client.keyManagerFactory());
    }

    final var address = cfg.getNetwork().toSocketAddress();
    final ManagedChannel channel =
        NettyChannelBuilder.forAddress(address.getHostString(), address.getPort())
            .sslContext(sslContext.build())
            .build();
    try {
      HealthGrpc.newBlockingStub(channel)
          .withDeadlineAfter(10, TimeUnit.SECONDS)
          .check(HealthCheckRequest.getDefaultInstance());
      return true;
    } catch (final StatusRuntimeException e) {
      return e.getStatus().getCode() != Code.UNAVAILABLE;
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private double acceptedCount(final String identity) {
    final var counter =
        meterRegistry
            .find("zeebe.gateway.grpc.client.certificate.accepted")
            .tag("identity", identity)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  private double rejectedCount(final String reason, final boolean enforced) {
    final var counter =
        meterRegistry
            .find("zeebe.gateway.grpc.client.certificate.rejected")
            .tag("reason", reason)
            .tag("enforced", String.valueOf(enforced))
            .counter();
    return counter == null ? 0 : counter.count();
  }

  private GatewayCfg createGatewayCfg() throws Exception {
    final var gatewayAddress = SocketUtil.getNextAddress();
    final var server = TestPki.newLeaf(serverCa, "gateway", "localhost", TestPki.loopback());
    return new GatewayCfg()
        .setNetwork(
            new NetworkCfg()
                .setHost(gatewayAddress.getHostName())
                .setPort(gatewayAddress.getPort()))
        .setSecurity(
            new SecurityCfg()
                .setEnabled(true)
                .setCertificateChainPath(
                    server.writeCertificateChain(tempDir.resolve("server.pem").toFile()))
                .setPrivateKeyPath(server.writePrivateKey(tempDir.resolve("server.key").toFile())));
  }

  private void startGateway() {
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
            cfg.getCluster().getRequestTimeout(),
            atomix.getMessagingService(),
            atomix.getEventService(),
            actorScheduler,
            topologyManager,
            new BrokerClientRequestMetrics(meterRegistry));
    jobStreamClient =
        new JobStreamClientImpl(actorScheduler, atomix.getCommunicationService(), meterRegistry);
    jobStreamClient.start().join();
    brokerClient.start().forEach(ActorFuture::join);
    topologyManager.addTopologyListener(jobStreamClient);
    atomix.getMembershipService().addListener(topologyManager);

    gateway =
        GatewayTestFactory.create(
            cfg,
            EngineSecurityConfigurations.defaultConfig(),
            brokerClient,
            actorScheduler,
            jobStreamClient.streamer(),
            mock(UserServices.class),
            mock(PasswordEncoder.class),
            meterRegistry,
            mock(JwtDecoder.class));
    gateway.start().join();
  }
}
