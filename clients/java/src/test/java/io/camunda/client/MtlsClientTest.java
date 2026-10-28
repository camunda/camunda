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
package io.camunda.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.camunda.client.api.response.Topology;
import io.camunda.client.impl.util.MtlsTestCertificates;
import io.camunda.client.util.RecordingGatewayService;
import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ExecutionException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the client presents its mTLS client certificate on both the gRPC and the REST
 * transport against servers that require client authentication.
 */
class MtlsClientTest {

  private static final MtlsTestCertificates CERTIFICATES = new MtlsTestCertificates();

  @TempDir static Path tempDir;

  private static Path serverCertPath;
  private static Path serverKeyPath;
  private static Path caPath;
  private static Path clientCertPath;
  private static Path clientKeyPath;
  private static Path untrustedClientCertPath;
  private static Path untrustedClientKeyPath;

  private Server grpcServer;
  private HttpsServer restServer;
  private URI grpcAddress;
  private URI restAddress;

  @BeforeAll
  static void writeCertificates() {
    serverCertPath = write("server.pem", MtlsTestCertificates.toPem(CERTIFICATES.serverCertificate));
    serverKeyPath =
        write("server-key.pem", MtlsTestCertificates.toPkcs8Pem(CERTIFICATES.serverKeyPair.getPrivate()));
    caPath = write("ca.pem", MtlsTestCertificates.toPem(CERTIFICATES.caCertificate));
    clientCertPath = write("client.pem", MtlsTestCertificates.toPem(CERTIFICATES.clientCertificate));
    clientKeyPath =
        write("client-key.pem", MtlsTestCertificates.toPkcs8Pem(CERTIFICATES.clientKeyPair.getPrivate()));
    untrustedClientCertPath =
        write("untrusted-client.pem", MtlsTestCertificates.toPem(CERTIFICATES.untrustedClientCertificate));
    untrustedClientKeyPath =
        write(
            "untrusted-client-key.pem",
            MtlsTestCertificates.toPkcs8Pem(CERTIFICATES.untrustedClientKeyPair.getPrivate()));
  }

  private static Path write(final String name, final String content) {
    try {
      return Files.write(tempDir.resolve(name), content.getBytes(StandardCharsets.US_ASCII));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void startServers() throws Exception {
    // gRPC server requiring client certificates
    final SslContextBuilder grpcSsl =
        GrpcSslContexts.forServer(serverCertPath.toFile(), serverKeyPath.toFile())
            .trustManager(caPath.toFile())
            .clientAuth(ClientAuth.REQUIRE);
    grpcServer =
        NettyServerBuilder.forAddress(new InetSocketAddress("localhost", 0))
            .addService(new RecordingGatewayService())
            .sslContext(grpcSsl.build())
            .build()
            .start();
    grpcAddress = URI.create("https://localhost:" + grpcServer.getPort());

    // REST server requiring client certificates
    restServer = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    restServer.setHttpsConfigurator(new HttpsConfigurator(createServerSslContext()));
    restServer.createContext("/v2/topology", MtlsClientTest::handleTopology);
    restServer.start();
    restAddress = URI.create("https://localhost:" + restServer.getAddress().getPort());
  }

  private static SSLContext createServerSslContext() throws Exception {
    final KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null);
    keyStore.setKeyEntry(
        "server",
        CERTIFICATES.serverKeyPair.getPrivate(),
        new char[0],
        new java.security.cert.Certificate[] {
          CERTIFICATES.serverCertificate, CERTIFICATES.caCertificate
        });
    final KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, new char[0]);

    final KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null);
    trustStore.setCertificateEntry("ca", CERTIFICATES.caCertificate);
    final TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);

    final SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
    return context;
  }

  private static void handleTopology(final HttpExchange exchange) throws IOException {
    final byte[] body =
        ("{\"brokers\":[],\"clusterSize\":0,\"partitionsCount\":0,\"replicationFactor\":0,"
                + "\"gatewayVersion\":\"test\"}")
            .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  @AfterAll
  void stopServers() {
    if (grpcServer != null) {
      grpcServer.shutdownNow();
    }
    if (restServer != null) {
      restServer.stop(0);
    }
  }

  private CamundaClientBuilder mtlsBuilder() {
    startServersUnchecked();
    return CamundaClient.newClientBuilder()
        .applyEnvironmentVariableOverrides(false)
        .grpcAddress(grpcAddress)
        .restAddress(restAddress)
        .clientCertificateCaPath(caPath.toString());
  }

  private void startServersUnchecked() {
    if (grpcServer == null) {
      try {
        startServers();
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Nested
  class Grpc {

    @Test
    void shouldSucceedWithValidClientCertificate() {
      // given / when / then
      try (final CamundaClient client =
          mtlsBuilder()
              .clientCertificatePath(clientCertPath.toString())
              .clientCertificateKeyPath(clientKeyPath.toString())
              .build()) {
        final Topology topology = client.newTopologyRequest().useGrpc().send().join();
        assertThat(topology).isNotNull();
      }
    }

    @Test
    void shouldSucceedWithInlinePemClientCertificate() {
      // given / when / then
      try (final CamundaClient client =
          mtlsBuilder()
              .clientCertificate(MtlsTestCertificates.toPem(CERTIFICATES.clientCertificate))
              .clientCertificateKey(
                  MtlsTestCertificates.toPkcs8Pem(CERTIFICATES.clientKeyPair.getPrivate()))
              .build()) {
        final Topology topology = client.newTopologyRequest().useGrpc().send().join();
        assertThat(topology).isNotNull();
      }
    }

    @Test
    void shouldFailWithoutClientCertificate() {
      // given / when / then
      try (final CamundaClient client = mtlsBuilder().build()) {
        assertThatThrownBy(() -> client.newTopologyRequest().useGrpc().send().join())
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(io.camunda.client.api.command.ClientStatusException.class);
      }
    }

    @Test
    void shouldFailWithUntrustedClientCertificate() {
      // given / when / then
      try (final CamundaClient client =
          mtlsBuilder()
              .clientCertificatePath(untrustedClientCertPath.toString())
              .clientCertificateKeyPath(untrustedClientKeyPath.toString())
              .build()) {
        assertThatThrownBy(() -> client.newTopologyRequest().useGrpc().send().join())
            .isInstanceOf(ExecutionException.class)
            .cause()
            .isInstanceOf(io.camunda.client.api.command.ClientStatusException.class);
      }
    }
  }

  @Nested
  class Rest {

    @Test
    void shouldSucceedWithValidClientCertificate() {
      // given / when / then
      try (final CamundaClient client =
          mtlsBuilder()
              .clientCertificatePath(clientCertPath.toString())
              .clientCertificateKeyPath(clientKeyPath.toString())
              .build()) {
        final Topology topology = client.newTopologyRequest().useRest().send().join();
        assertThat(topology).isNotNull();
      }
    }

    @Test
    void shouldFailWithoutClientCertificate() {
      // given / when / then
      try (final CamundaClient client = mtlsBuilder().build()) {
        assertThatThrownBy(() -> client.newTopologyRequest().useRest().send().join())
            .cause()
            .isInstanceOf(io.camunda.client.api.command.ClientException.class);
      }
    }

    @Test
    void shouldFailWithUntrustedClientCertificate() {
      // given / when / then
      try (final CamundaClient client =
          mtlsBuilder()
              .clientCertificatePath(untrustedClientCertPath.toString())
              .clientCertificateKeyPath(untrustedClientKeyPath.toString())
              .build()) {
        assertThatThrownBy(() -> client.newTopologyRequest().useRest().send().join())
            .cause()
            .isInstanceOf(io.camunda.client.api.command.ClientException.class);
      }
    }
  }
}
