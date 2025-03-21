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
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.NetworkCfg;
import io.camunda.zeebe.gateway.impl.configuration.SecurityCfg;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.Certificate;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
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
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

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
  void shouldStartWithTlsEnabledWithPasswordProtectedKeyStoreFile() {
    // given
    final var cfg = createGatewayCfg();
    final var pkcs12 = createPKCS12File();
    cfg.getSecurity()
        .setEnabled(true)
        .setCertificateChainPath(null)
        .setPrivateKeyPath(null)
        .getKeyStore()
        .setFilePath(pkcs12)
        .setPassword("password");

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

  private File createPKCS12File() {
    try {
      final var store = KeyStore.getInstance("PKCS12");
      final var chain = new Certificate[] {certificate.cert()};
      final var file = Files.createTempFile("id", ".p12").toFile();

      store.load(null, null);
      store.setKeyEntry("key", certificate.key(), "password".toCharArray(), chain);

      try (final var fOut = new FileOutputStream(file)) {
        store.store(fOut, "password".toCharArray());
      }

      return file;
    } catch (final Exception e) {
      throw new RuntimeException("Failed to create PKCS12 file", e);
    }
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
    final var clusterConfiguration =
        new AtomixClusterConfiguration(clusterConfig, null, meterRegistry);
    atomixCluster = clusterConfiguration.atomixCluster();
    final ActorSchedulerConfiguration actorSchedulerConfiguration =
        new ActorSchedulerConfiguration(
            schedulerConfig,
            IdleStrategySupplier.ofDefault(),
            new ActorClockConfiguration(false),
            meterRegistry);

    actorScheduler = actorSchedulerConfiguration.scheduler();
    final var topologyServices =
        new DynamicClusterServices(actorScheduler, atomixCluster, meterRegistry);
    final var topologyManager = topologyServices.brokerTopologyManager();
    topologyServices.gatewayClusterTopologyService(topologyManager, gatewayCfg);

    final var brokerClientConfiguration =
        new BrokerClientConfiguration(
            brokerClientConfig, atomixCluster, actorScheduler, topologyManager, meterRegistry);
    brokerClient = brokerClientConfiguration.brokerClient();
    jobStreamClient =
        new JobStreamComponent().jobStreamClient(actorScheduler, atomixCluster, meterRegistry);

    return new GatewayModuleConfiguration(
        gatewayConfig,
        new SecurityConfiguration(),
        new SpringGatewayBridge(),
        actorScheduler,
        atomixCluster,
        brokerClient,
        jobStreamClient,
        null,
        null,
        null,
        new SimpleMeterRegistry());
  }
}
