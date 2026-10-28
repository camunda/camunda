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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.response.Topology;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the REST connection against a server that requires mutual TLS: a valid client
 * certificate succeeds, while a missing or an untrusted client certificate fails the TLS handshake.
 */
class RestMtlsTest {

  private static final String RESOURCES = "src/test/resources/mtls/";
  private static final File SERVER_KEYSTORE = new File(RESOURCES + "server.p12");
  private static final File CA_CERT = new File(RESOURCES + "ca.crt");
  private static final File CLIENT_CERT = new File(RESOURCES + "client.crt");
  private static final File CLIENT_KEY = new File(RESOURCES + "client.key");
  private static final File CLIENT_P12 = new File(RESOURCES + "client.p12");
  private static final File TRUSTSTORE_P12 = new File(RESOURCES + "truststore.p12");
  private static final File UNTRUSTED_CLIENT_CERT = new File(RESOURCES + "untrusted-client.crt");
  private static final File UNTRUSTED_CLIENT_KEY = new File(RESOURCES + "untrusted-client.key");

  private HttpsServer server;
  private URI restAddress;

  @BeforeEach
  void startMtlsServer() throws Exception {
    final KeyStore serverKeys = KeyStore.getInstance("PKCS12");
    try (final FileInputStream in = new FileInputStream(SERVER_KEYSTORE)) {
      serverKeys.load(in, "serverpass".toCharArray());
    }
    final KeyManagerFactory kmf =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(serverKeys, "serverpass".toCharArray());

    final KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
    truststore.load(null);
    try (final FileInputStream in = new FileInputStream(CA_CERT)) {
      truststore.setCertificateEntry(
          "ca", java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in));
    }
    final TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(truststore);

    final SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

    server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setHttpsConfigurator(
        new HttpsConfigurator(sslContext) {
          @Override
          public void configure(final com.sun.net.httpserver.HttpsParameters params) {
            final javax.net.ssl.SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
            sslParameters.setNeedClientAuth(true);
            params.setSSLParameters(sslParameters);
          }
        });
    server.createContext("/v2/topology", RestMtlsTest::handleTopology);
    server.start();
    restAddress = URI.create("https://localhost:" + server.getAddress().getPort());
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  private static void handleTopology(final HttpExchange exchange) throws java.io.IOException {
    final byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private static String readResource(final File file) throws java.io.IOException {
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }

  private CamundaClientBuilderImpl clientBuilder() {
    return (CamundaClientBuilderImpl)
        CamundaClient.newClientBuilder()
            .applyEnvironmentVariableOverrides(false)
            .grpcAddress(URI.create("https://localhost:126500"))
            .restAddress(restAddress)
            .preferRestOverGrpc(true)
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
      final Topology topology = client.newTopologyRequest().useRest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldConnectWithInlinePemClientCertificate() throws Exception {
    // given
    final CamundaClientBuilder builder =
        clientBuilder()
            .clientCertificateContent(readResource(CLIENT_CERT), readResource(CLIENT_KEY))
            .mtlsCaCertificateContent(readResource(CA_CERT));

    // when
    try (final CamundaClient client = builder.build()) {
      final Topology topology = client.newTopologyRequest().useRest().send().join();

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
      final Topology topology = client.newTopologyRequest().useRest().send().join();

      // then
      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldFailWithoutClientCertificate() {
    // given - the server requires a client certificate, but none is configured
    final CamundaClientBuilder builder =
        clientBuilder().mtlsCaCertificatePath(CA_CERT.getAbsolutePath());

    // when / then - the handshake is rejected because no client certificate is presented
    try (final CamundaClient client = builder.build()) {
      assertThatThrownBy(() -> client.newTopologyRequest().useRest().send().join())
          .satisfies(e -> assertThat(rootCauses(e)).anyMatch(RestMtlsTest::isTlsFailure));
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
      assertThatThrownBy(() -> client.newTopologyRequest().useRest().send().join())
          .satisfies(e -> assertThat(rootCauses(e)).anyMatch(RestMtlsTest::isTlsFailure));
    }
  }

  /**
   * A rejected mTLS handshake surfaces differently per TLS stack: an {@link SSLHandshakeException}
   * when the alert arrives during the handshake, or a closed connection ({@code
   * ConnectionClosedException} / {@link java.net.SocketException}) when the server aborts first.
   */
  private static boolean isTlsFailure(final Throwable t) {
    return t instanceof javax.net.ssl.SSLException
        || t instanceof org.apache.hc.core5.http.ConnectionClosedException
        || t instanceof java.net.SocketException;
  }

  private static List<Throwable> rootCauses(final Throwable throwable) {
    final List<Throwable> chain = new ArrayList<>();
    Throwable current = throwable;
    while (current != null) {
      chain.add(current);
      current = current.getCause();
    }
    return chain;
  }
}
