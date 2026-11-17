/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.security;

import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.SslAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Spring Boot applies {@code server.ssl.*} to the main connector only. The dedicated cluster API
 * port must inherit it, or cluster-admin credentials would travel in plain text on a cluster whose
 * REST API uses TLS.
 */
@ZeebeIntegration
final class ClusterApiDedicatedPortTlsIT {

  private static final SelfSignedCertificate CERTIFICATE = newCertificate();
  private static final int CLUSTER_PORT = SocketUtil.getNextAddress().getPort();

  // the REST client used to await the topology does not trust the self-signed certificate
  @TestZeebe(awaitCompleteTopology = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          // the dedicated port only opens alongside a cluster-admin security chain, and Basic
          // authentication requires secondary storage
          .withBasicAuth()
          .withUnauthenticatedAccess()
          .withSecondaryStorageType(SecondaryStorageType.rdbms)
          .withUnifiedConfig(
              cfg -> {
                final var rdbms = cfg.getData().getSecondaryStorage().getRdbms();
                rdbms.setUrl(
                    "jdbc:h2:mem:cluster-api-tls-"
                        + UUID.randomUUID()
                        + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
                rdbms.setUsername("sa");
                rdbms.setPassword("");
              })
          .withProperty("server.ssl.enabled", true)
          .withProperty("server.ssl.certificate", CERTIFICATE.certificate().getAbsolutePath())
          .withProperty(
              "server.ssl.certificate-private-key", CERTIFICATE.privateKey().getAbsolutePath())
          .withProperty("camunda.api.cluster.port", CLUSTER_PORT);

  @Test
  void shouldSecureDedicatedClusterPortWithMainPortCertificate() {
    // when / then
    SslAssert.assertThat(new InetSocketAddress(broker.host(), CLUSTER_PORT))
        .isSecuredBy(CERTIFICATE);
  }

  @Test
  void shouldStillSecureMainPort() {
    // when / then
    SslAssert.assertThat(
            new InetSocketAddress(broker.host(), broker.mappedPort(TestZeebePort.REST)))
        .isSecuredBy(CERTIFICATE);
  }

  private static SelfSignedCertificate newCertificate() {
    try {
      return new SelfSignedCertificate();
    } catch (final CertificateException e) {
      throw new IllegalStateException(e);
    }
  }
}
