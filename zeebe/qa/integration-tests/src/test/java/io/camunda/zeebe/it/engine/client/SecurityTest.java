/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.response.Topology;
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.File;
import java.net.URL;
import java.security.cert.CertificateException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.jmh.annotations.Timeout;
import org.testcontainers.junit.jupiter.Testcontainers;

@Timeout(time = 120)
@Testcontainers
@ZeebeIntegration
public final class SecurityTest {

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withAuthorizationsDisabled()
          .withUnifiedConfig(this::configureClusterForTls);

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldEstablishSecureConnection(final boolean useRest) {
    // given
    try (final var client = newSecureClient(useRest).build()) {

      // when
      final var result = client.newTopologyRequest().send().join();

      // then
      assertThat(result.getBrokers().size()).isEqualTo(1);
    }
  }

  @Test
  void shouldAllowToOverrideAuthority() {
    // given an overridden authority (only works for gRPC)
    try (final var client = newSecureClient(false).overrideAuthority("localhost").build()) {

      // when
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology.getBrokers().size()).isEqualTo(1);
    }
  }

  @Test
  void shouldRejectDifferentAuthority() {
    // given an unknown authority (only works for gRPC)
    try (final var client = newSecureClient(false).overrideAuthority("virtualhost").build()) {
      // when, then
      Assertions.assertThatThrownBy(() -> client.newTopologyRequest().send().join())
          .hasRootCauseInstanceOf(CertificateException.class)
          .hasRootCauseMessage("No name matching virtualhost found");
    }
  }

  private CamundaClientBuilder newSecureClient(final boolean useRest) {
    return configureClientForTls(broker.newClientBuilder()).preferRestOverGrpc(useRest);
  }

  private CamundaClientBuilder configureClientForTls(final CamundaClientBuilder clientBuilder) {
    return clientBuilder.caCertificatePath(getResource("security/test-chain.cert.pem").getPath());
  }

  private void configureClusterForTls(final Camunda camunda) {
    final String certificatePath = getResource("security/test-chain.cert.pem").getFile();
    final String privateKeyPath = getResource("security/test-server.key.pem").getFile();

    // configure the server for TLS/SSL
    final var ssl = camunda.getApi().getGrpc().getSsl();
    ssl.setEnabled(true);
    ssl.setCertificate(new File(certificatePath));
    ssl.setCertificatePrivateKey(new File(privateKeyPath));
  }

  private URL getResource(final String name) {
    return getClass().getClassLoader().getResource(name);
  }
}
