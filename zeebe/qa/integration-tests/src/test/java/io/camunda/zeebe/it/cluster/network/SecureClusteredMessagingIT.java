/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.network;

import io.atomix.utils.net.Address;
import io.camunda.client.api.response.Topology;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ZeebeIntegration
final class SecureClusteredMessagingIT {
  private static final SelfSignedCertificate CERTIFICATE = newCertificate();

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withPartitionsCount(1)
          .withReplicationFactor(2)
          .withBrokersCount(2)
          .withGatewaysCount(1)
          .withEmbeddedGateway(false)
          .build();

  @ParameterizedTest
  @MethodSource("provideTestCases")
  void shouldFormAClusterWithTlsWithCertChain(final TestCase testCase) {
    // given - a cluster with 2 standalone brokers, and 1 standalone gateway
    cluster.brokers().values().forEach(node -> testCase.broker().accept(node));
    cluster.gateways().values().forEach(node -> testCase.gateway().accept(node));
    cluster.start().awaitCompleteTopology();

    // when - note the client is using plaintext since we only care about inter-cluster TLS
    final Topology topology;
    try (final var client = cluster.newClientBuilder().build()) {
      topology = client.newTopologyRequest().send().join(15, TimeUnit.SECONDS);
    }

    // then - ensure the cluster is formed correctly and all inter-cluster communication endpoints
    // are secured using the expected certificate
    TopologyAssert.assertThat(topology).hasBrokersCount(2).isComplete(2, 1, 2);
    cluster.brokers().values().forEach(this::assertBrokerMessagingServicesAreSecured);
    assertAddressIsSecured("gateway", getGatewayAddress(cluster));
  }

  private static Stream<Named<TestCase>> provideTestCases() {
    final var pkcs12File = createPKCS12File();

    return Stream.of(
        Named.of(
            "cert/key pair",
            new TestCase(
                SecureClusteredMessagingIT::configureCertChainBroker,
                SecureClusteredMessagingIT::configureCertChainGateway)),
        Named.of(
            "key store",
            new TestCase(
                config -> configureKeyStoreBroker(config, pkcs12File),
                config -> configureKeyStoreGateway(config, pkcs12File))));
  }

  /** Verifies that both the command and internal APIs of the broker are correctly secured. */
  private void assertBrokerMessagingServicesAreSecured(final TestStandaloneBroker broker) {
    // Use BrokerBasedProperties instead of unified configuration here because the
    // advertised addresses are not initialized. The unified configuration does not
    // provide an applyDefaults() method like NetworkCfg does for setting the addresses.
    final var brokerBasedProperties = broker.bean(BrokerBasedProperties.class);
    final var commandApiAddress =
        brokerBasedProperties.getNetwork().getCommandApi().getAdvertisedAddress();
    final var internalApiAddress =
        brokerBasedProperties.getNetwork().getInternalApi().getAdvertisedAddress();

    assertAddressIsSecured(brokerBasedProperties.getCluster().getNodeId(), commandApiAddress);
    assertAddressIsSecured(brokerBasedProperties.getCluster().getNodeId(), internalApiAddress);
  }

  private InetSocketAddress getGatewayAddress(final TestCluster cluster) {
    // Use GatewayBasedProperties instead of the unified configuration to resolve the
    // advertised address and port via ClusterCfg, because the getters contain logic
    // that is not available in the unified configuration.
    final ClusterCfg clusterConfig =
        cluster.availableGateway().bean(GatewayBasedProperties.class).getCluster();
    final var address =
        Address.from(clusterConfig.getAdvertisedHost(), clusterConfig.getAdvertisedPort());
    return address.socketAddress();
  }

  private void assertAddressIsSecured(final Object nodeId, final SocketAddress address) {
    SslAssert.assertThat(address)
        .as("node %s is not secured correctly at address %s", nodeId, address)
        .isSecuredBy(CERTIFICATE);
  }

  private static SelfSignedCertificate newCertificate() {
    try {
      return new SelfSignedCertificate();
    } catch (final CertificateException e) {
      throw new IllegalStateException("Failed to create self-signed certificate", e);
    }
  }

  private static File createPKCS12File() {
    try {
      final var store = KeyStore.getInstance("PKCS12");
      final var chain = new Certificate[] {CERTIFICATE.cert()};
      final var file = Files.createTempFile("id", ".p12").toFile();

      store.load(null, null);
      store.setKeyEntry("key", CERTIFICATE.key(), "password".toCharArray(), chain);

      try (final var fOut = new FileOutputStream(file)) {
        store.store(fOut, "password".toCharArray());
      }

      return file;
    } catch (final Exception e) {
      throw new RuntimeException("Failed to create PKCS12 file", e);
    }
  }

  private static void configureCertChainGateway(final TestStandaloneGateway gateway) {
    gateway.withUnifiedConfig(
        cfg -> {
          final var tlsCluster = cfg.getSecurity().getTransportLayerSecurity().getCluster();
          tlsCluster.setEnabled(true);
          tlsCluster.setCertificateChainPath(CERTIFICATE.certificate());
          tlsCluster.setCertificatePrivateKeyPath(CERTIFICATE.privateKey());
        });
  }

  private static void configureCertChainBroker(final TestStandaloneBroker broker) {
    broker.withUnifiedConfig(
        cfg -> {
          final var tlsCluster = cfg.getSecurity().getTransportLayerSecurity().getCluster();
          tlsCluster.setEnabled(true);
          tlsCluster.setCertificateChainPath(CERTIFICATE.certificate());
          tlsCluster.setCertificatePrivateKeyPath(CERTIFICATE.privateKey());
        });
  }

  private static void configureKeyStoreGateway(
      final TestStandaloneGateway gateway, final File pkcs12) {
    gateway.withUnifiedConfig(
        cfg -> {
          final var tlsCluster = cfg.getSecurity().getTransportLayerSecurity().getCluster();
          tlsCluster.setEnabled(true);
          tlsCluster.getKeyStore().setFilePath(pkcs12.getAbsoluteFile());
          tlsCluster.getKeyStore().setPassword("password");
        });
  }

  private static void configureKeyStoreBroker(
      final TestStandaloneBroker broker, final File pkcs12) {
    broker.withUnifiedConfig(
        cfg -> {
          final var tlsCluster = cfg.getSecurity().getTransportLayerSecurity().getCluster();
          tlsCluster.setEnabled(true);
          tlsCluster.getKeyStore().setFilePath(pkcs12.getAbsoluteFile());
          tlsCluster.getKeyStore().setPassword("password");
        });
  }

  private record TestCase(
      Consumer<TestStandaloneBroker> broker, Consumer<TestStandaloneGateway> gateway) {}
}
