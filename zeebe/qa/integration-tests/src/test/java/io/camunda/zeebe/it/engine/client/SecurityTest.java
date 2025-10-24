/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.response.Topology;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.File;
import java.net.URL;
import java.security.cert.CertificateException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.jmh.annotations.Timeout;
import org.testcontainers.junit.jupiter.Testcontainers;

@Timeout(time = 120)
@Testcontainers
@ZeebeIntegration
public final class SecurityTest {

  @TestZeebe(autoStart = false)
  private final TestCluster testCluster =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokersCount(1)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withGatewayConfig(this::configureGatewayForTls)
          .build();

  @BeforeEach
  void setUp() {
    // When starting a node with SSL enabled, Tomcat will initialize
    // a static map of cipher aliases in OpenSSLCipherConfigurationParser.
    // When starting multiple nodes simultaneously, it may fail because
    // they modify the static map concurrently.
    // To avoid the concurrent initialization of the static map, the nodes
    // must be started one by one.
    final var nodes = testCluster.nodes().values();
    nodes.forEach(
        node -> {
          node.start();
        });
    testCluster.awaitCompleteTopology();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldEstablishSecureConnectionWithGrpc(final boolean useRest) {
    // given
    try (final var client = newSecureClient(useRest).build()) {

      // when
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology.getBrokers().size()).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldAllowToOverrideAuthority(final boolean useRest) {
    // given
    try (final var client = newSecureClient(useRest).overrideAuthority("localhost").build()) {

      // when
      final Topology topology = client.newTopologyRequest().useGrpc().send().join();

      // then
      assertThat(topology.getBrokers().size()).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRejectDifferentAuthority(final boolean useRest) {
    // given
    try (final var client = newSecureClient(useRest).overrideAuthority("virtualhost").build()) {
      // when, then
      Assertions.assertThatThrownBy(() -> client.newTopologyRequest().useGrpc().send().join())
          .hasRootCauseInstanceOf(CertificateException.class)
          .hasRootCauseMessage("No name matching virtualhost found");
    }
  }

  private CamundaClientBuilder newSecureClient(final boolean useRest) {
    return configureClientForTls(CamundaClient.newClientBuilder())
        .preferRestOverGrpc(useRest)
        .grpcAddress(testCluster.anyGateway().grpcAddress())
        .restAddress(testCluster.anyGateway().restAddress());
  }

  private CamundaClientBuilder configureClientForTls(final CamundaClientBuilder clientBuilder) {
    return clientBuilder.caCertificatePath(getResource("security/test-chain.cert.pem").getPath());
  }

  private void configureGatewayForTls(final TestGateway gateway) {
    final String certificatePath = getResource("security/test-chain.cert.pem").getFile();
    final String privateKeyPath = getResource("security/test-server.key.pem").getFile();

    // configure the gRPC server for TLS/SSL
    gateway
        .gatewayConfig()
        .getSecurity()
        .setEnabled(true)
        .setCertificateChainPath(new File(certificatePath))
        .setPrivateKeyPath(new File(privateKeyPath));

    // configure the REST API server for TLS/SSL
    gateway
        .withProperty("server.ssl.enabled", true)
        .withProperty("server.ssl.certificate", certificatePath)
        .withProperty("server.ssl.certificate-private-key", privateKeyPath);
  }

  private URL getResource(final String name) {
    return getClass().getClassLoader().getResource(name);
  }
}
