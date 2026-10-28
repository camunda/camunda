/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.tls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.response.Topology;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayImplBase;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TopologyResponse;
import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.ClientAuth;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the gRPC connection against a server that requires mutual TLS: a valid client
 * certificate succeeds, while a missing or an untrusted client certificate fails the TLS handshake.
 */
class GrpcMtlsTest {

  private static final String RESOURCES = "src/test/resources/mtls/";
  private static final File SERVER_CERT = new File(RESOURCES + "server.crt");
  private static final File SERVER_KEY = new File(RESOURCES + "server.key");
  private static final File CA_CERT = new File(RESOURCES + "ca.crt");
  private static final File CLIENT_CERT = new File(RESOURCES + "client.crt");
  private static final File CLIENT_KEY = new File(RESOURCES + "client.key");
  private static final File CLIENT_KEY_ENCRYPTED = new File(RESOURCES + "client-encrypted.key");
  private static final File CLIENT_EC_CERT = new File(RESOURCES + "client-ec.crt");
  private static final File CLIENT_EC_KEY = new File(RESOURCES + "client-ec.key");
  private static final File CLIENT_P12 = new File(RESOURCES + "client.p12");
  private static final File CLIENT_MULTI_P12 = new File(RESOURCES + "client-multi.p12");
  private static final File TRUSTSTORE_P12 = new File(RESOURCES + "truststore.p12");
  private static final File TRUSTSTORE_JKS = new File(RESOURCES + "truststore.jks");
  private static final File UNTRUSTED_CLIENT_CERT = new File(RESOURCES + "untrusted-client.crt");
  private static final File UNTRUSTED_CLIENT_KEY = new File(RESOURCES + "untrusted-client.key");

  private Server server;
  private URI grpcAddress;

  @BeforeEach
  void startMtlsServer() throws Exception {
    server =
        NettyServerBuilder.forPort(0)
            .addService(new NoopGateway())
            .sslContext(
                GrpcSslContexts.forServer(SERVER_CERT, SERVER_KEY)
                    .clientAuth(ClientAuth.REQUIRE)
                    .trustManager(CA_CERT)
                    .build())
            .build()
            .start();
    grpcAddress = URI.create("https://localhost:" + server.getPort());
  }

  @AfterEach
  void stopServer() throws Exception {
    if (server != null) {
      server.shutdownNow();
      server.awaitTermination(10, TimeUnit.SECONDS);
    }
  }

  private CamundaClientBuilderImpl clientBuilder() {
    return (CamundaClientBuilderImpl)
        CamundaClient.newClientBuilder()
            .applyEnvironmentVariableOverrides(false)
            .grpcAddress(grpcAddress)
            .restAddress(URI.create("https://localhost:18080"))
            // exercise the gRPC transport; the default would prefer REST
            .preferRestOverGrpc(false)
            .defaultRequestTimeout(Duration.ofSeconds(10));
  }

  @Test
  void shouldConnectWithValidPemClientCertificate() {
    // given
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientCertificate(CLIENT_CERT.getAbsolutePath(), CLIENT_KEY.getAbsolutePath())
            .mtlsCaCertificatePath(CA_CERT.getAbsolutePath());

    // when
    try (final CamundaClient client = builder.build()) {
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldConnectWithInlinePemClientCertificate() throws Exception {
    // given
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientCertificateContent(
                new String(
                    java.nio.file.Files.readAllBytes(CLIENT_CERT.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8),
                new String(
                    java.nio.file.Files.readAllBytes(CLIENT_KEY.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8))
            .mtlsCaCertificateContent(
                new String(
                    java.nio.file.Files.readAllBytes(CA_CERT.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8));

    // when
    try (final CamundaClient client = builder.build()) {
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldConnectWithEncryptedKeyAndPassphrase() {
    // given
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientCertificate(
                CLIENT_CERT.getAbsolutePath(), CLIENT_KEY_ENCRYPTED.getAbsolutePath())
            .clientKeyPassphrase("test1234")
            .mtlsCaCertificatePath(CA_CERT.getAbsolutePath());

    // when
    try (final CamundaClient client = builder.build()) {
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldConnectWithEcClientCertificate() {
    // given
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientCertificate(CLIENT_EC_CERT.getAbsolutePath(), CLIENT_EC_KEY.getAbsolutePath())
            .mtlsCaCertificatePath(CA_CERT.getAbsolutePath());

    // when
    try (final CamundaClient client = builder.build()) {
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldConnectWithPkcs12KeystoreAndTruststore() {
    // given
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientKeystore(CLIENT_P12.getAbsolutePath(), "storepass", null)
            .mtlsTruststore(TRUSTSTORE_P12.getAbsolutePath(), "trustpass");

    // when
    try (final CamundaClient client = builder.build()) {
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldSelectTrustedIdentityFromMultiEntryKeystore() {
    // given - the keystore's first key entry is signed by a CA the server does not trust; the
    // trusted identity sits under a later alias, so the key manager must choose by the server's
    // accepted issuers rather than taking the first entry
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientKeystore(CLIENT_MULTI_P12.getAbsolutePath(), "storepass", null)
            .mtlsTruststore(TRUSTSTORE_P12.getAbsolutePath(), "trustpass");

    // when
    try (final CamundaClient client = builder.build()) {
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldConnectWithJksTruststore() {
    // given
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientCertificate(CLIENT_CERT.getAbsolutePath(), CLIENT_KEY.getAbsolutePath())
            .mtlsTruststore(TRUSTSTORE_JKS.getAbsolutePath(), "trustpass");

    // when
    try (final CamundaClient client = builder.build()) {
      final Topology topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldFailWithoutClientCertificate() {
    // given - the server requires a client certificate, but none is configured
    final CamundaClientBuilder builder =
        clientBuilder().mtlsCaCertificatePath(CA_CERT.getAbsolutePath());

    // when / then
    try (final CamundaClient client = builder.build()) {
      assertThatThrownBy(() -> client.newTopologyRequest().send().join())
          .satisfies(e -> assertThat(rootCauses(e)).anyMatch(GrpcMtlsTest::isTlsHandshakeFailure));
    }
  }

  @Test
  void shouldFailWithUntrustedClientCertificate() {
    // given - a client certificate signed by a CA the server does not trust
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientCertificate(
                UNTRUSTED_CLIENT_CERT.getAbsolutePath(), UNTRUSTED_CLIENT_KEY.getAbsolutePath())
            .mtlsCaCertificatePath(CA_CERT.getAbsolutePath());

    // when / then
    try (final CamundaClient client = builder.build()) {
      assertThatThrownBy(() -> client.newTopologyRequest().send().join())
          .satisfies(e -> assertThat(rootCauses(e)).anyMatch(GrpcMtlsTest::isTlsHandshakeFailure));
    }
  }

  /**
   * The concrete handshake failure type depends on the TLS engine in use (JDK vs. OpenSSL via
   * netty-tcnative), so assert on the common SSL failure family.
   */
  private static boolean isTlsHandshakeFailure(final Throwable t) {
    return t instanceof javax.net.ssl.SSLException;
  }

  private static java.util.List<Throwable> rootCauses(final Throwable throwable) {
    final java.util.List<Throwable> chain = new java.util.ArrayList<>();
    Throwable current = throwable;
    while (current != null) {
      chain.add(current);
      current = current.getCause();
    }
    return chain;
  }

  private static final class NoopGateway extends GatewayImplBase {
    @Override
    public void topology(
        final TopologyRequest request, final StreamObserver<TopologyResponse> responseObserver) {
      responseObserver.onNext(TopologyResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }
}
