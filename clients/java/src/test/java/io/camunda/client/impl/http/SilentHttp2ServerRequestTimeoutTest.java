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
package io.camunda.client.impl.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.worker.JobWorker;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for camunda/camunda#65131. httpclient5 5.6.1+ does not apply the response timeout
 * on HTTP/2 connections, so a server that completes the HTTP/2 handshake but never answers used to
 * leave the request pending forever.
 */
class SilentHttp2ServerRequestTimeoutTest {

  private static final char[] PASSWORD = "changeit".toCharArray();
  // shortened from the 5s default so that the tests do not wait for the full margin (see
  // CamundaClientBuilder#responseDeadlineMargin)
  private static final Duration RESPONSE_DEADLINE_MARGIN = Duration.ofSeconds(1);
  private static final long KEYTOOL_TIMEOUT_SECONDS = 60;

  private static ProxySelector originalProxySelector;

  @TempDir Path tempDir;

  private final AtomicInteger receivedRequests = new AtomicInteger();
  private final AtomicInteger connectedSessions = new AtomicInteger();
  private final AtomicInteger disconnectedSessions = new AtomicInteger();
  private HttpAsyncServer server;
  private int port;
  private Path certificatePath;
  private CamundaClient client;

  /** Keeps a system-configured proxy from intercepting the requests to the local server. */
  @BeforeAll
  static void disableSystemProxy() {
    originalProxySelector = ProxySelector.getDefault();
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(final URI uri) {
            return Collections.singletonList(Proxy.NO_PROXY);
          }

          @Override
          public void connectFailed(
              final URI uri, final SocketAddress address, final IOException exception) {}
        });
  }

  @AfterAll
  static void restoreSystemProxy() {
    ProxySelector.setDefault(originalProxySelector);
  }

  @BeforeEach
  void setUp() throws Exception {
    final Path keyStorePath = tempDir.resolve("server.p12");
    certificatePath = tempDir.resolve("server.pem");
    generateSelfSignedCertificate(keyStorePath, certificatePath);

    server =
        H2ServerBootstrap.bootstrap()
            .setTlsStrategy(new H2ServerTlsStrategy(createSslContext(keyStorePath)))
            .setIOSessionListener(new SessionCounter())
            .setRequestRouter(
                (request, context) -> () -> new BasicServerExchangeHandler<>(new SilentHandler()))
            .create();
    server.start();
    final ListenerEndpoint endpoint =
        server.listen(new InetSocketAddress("localhost", 0), URIScheme.HTTPS).get();
    port = ((InetSocketAddress) endpoint.getAddress()).getPort();

    client = clientBuilder().build();
  }

  private CamundaClientBuilder clientBuilder() throws Exception {
    return CamundaClient.newClientBuilder()
        .applyEnvironmentVariableOverrides(false)
        .preferRestOverGrpc(true)
        .restAddress(new URI("https://localhost:" + port))
        .caCertificatePath(certificatePath.toString())
        .responseDeadlineMargin(RESPONSE_DEADLINE_MARGIN);
  }

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      server.close(CloseMode.IMMEDIATE);
    }
  }

  @Test
  void shouldFailActivationWhenServerNeverAnswers() {
    // given
    final Future<ActivateJobsResponse> activation =
        client
            .newActivateJobsCommand()
            .jobType("silent")
            .maxJobsToActivate(1)
            .requestTimeout(Duration.ofSeconds(1))
            .send();

    // when / then: request timeout 1s + offset + margin, well below the 30s patience
    assertThatThrownBy(() -> activation.get(30, TimeUnit.SECONDS))
        .isNotInstanceOf(TimeoutException.class)
        .isInstanceOf(ExecutionException.class);
    assertThat(receivedRequests.get()).isPositive();
    // the connection is closed, not just the future failed, so its lease is released
    await().untilAsserted(() -> assertThat(disconnectedSessions.get()).isPositive());
  }

  @Test
  void shouldNotCountWaitForConnectionTowardsDeadlineOfRequestWithoutBody() throws Exception {
    // given the only pooled connection is occupied by a request that never gets an answer
    try (final CamundaClient singleConnectionClient =
        clientBuilder()
            .maxHttpConnections(1)
            .defaultRequestTimeout(Duration.ofSeconds(1))
            .build()) {
      singleConnectionClient
          .newActivateJobsCommand()
          .jobType("silent")
          .maxJobsToActivate(1)
          .requestTimeout(Duration.ofSeconds(1))
          .send();
      await().until(() -> receivedRequests.get() == 1);

      // when a request without a body has to wait for that connection for longer than its own
      // response timeout plus margin (2s), because the first request only fails after 3s
      final Future<?> waiting = singleConnectionClient.newTopologyRequest().send();

      // then its deadline only starts once it has the connection, so it is sent after all
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(() -> assertThat(receivedRequests.get()).isEqualTo(2));
      assertThatThrownBy(() -> waiting.get(30, TimeUnit.SECONDS))
          .isNotInstanceOf(TimeoutException.class)
          .isInstanceOf(ExecutionException.class);
    }
  }

  @Test
  void shouldKeepPollingAfterPollsFail() {
    // given
    final JobWorker worker =
        client
            .newWorker()
            .jobType("silent")
            .handler((jobClient, job) -> {})
            .requestTimeout(Duration.ofSeconds(1))
            .pollInterval(Duration.ofMillis(100))
            .open();

    // when / then: the poller is released after a failed poll, so another request is sent, and
    // because the first connection was closed, it has to use a new one
    try {
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                assertThat(receivedRequests.get()).isGreaterThanOrEqualTo(2);
                assertThat(disconnectedSessions.get()).isPositive();
                assertThat(connectedSessions.get()).isGreaterThanOrEqualTo(2);
              });
    } finally {
      worker.close();
    }
  }

  /** Creates a throwaway key pair for localhost with the JDK's keytool, so no key is committed. */
  private void generateSelfSignedCertificate(final Path keyStore, final Path certificate)
      throws Exception {
    final Path binDirectory = Paths.get(System.getProperty("java.home"), "bin");
    final Path keytool =
        Files.exists(binDirectory.resolve("keytool.exe"))
            ? binDirectory.resolve("keytool.exe")
            : binDirectory.resolve("keytool");
    if (!Files.exists(keytool)) {
      fail("keytool is needed to create the test certificate but is not in %s", binDirectory);
    }
    run(
        keytool.toString(),
        "-genkeypair",
        "-alias",
        "server",
        "-keyalg",
        "RSA",
        "-keysize",
        "2048",
        "-dname",
        "CN=localhost",
        "-ext",
        "san=dns:localhost",
        "-validity",
        "1",
        "-storetype",
        "PKCS12",
        "-keystore",
        keyStore.toString(),
        "-storepass",
        new String(PASSWORD));
    run(
        keytool.toString(),
        "-exportcert",
        "-rfc",
        "-alias",
        "server",
        "-keystore",
        keyStore.toString(),
        "-storepass",
        new String(PASSWORD),
        "-file",
        certificate.toString());
  }

  private void run(final String... command) throws Exception {
    final Path log = Files.createTempFile(tempDir, "keytool", ".log");
    final Process process =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    if (!process.waitFor(KEYTOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      fail("keytool did not finish within %d seconds", KEYTOOL_TIMEOUT_SECONDS);
    }
    assertThat(process.exitValue())
        .describedAs(new String(Files.readAllBytes(log), StandardCharsets.UTF_8))
        .isZero();
  }

  private SSLContext createSslContext(final Path keyStorePath) throws Exception {
    final KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (final InputStream in = Files.newInputStream(keyStorePath)) {
      keyStore.load(in, PASSWORD);
    }
    final KeyManagerFactory factory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    factory.init(keyStore, PASSWORD);
    final SSLContext context = SSLContext.getInstance("TLS");
    context.init(factory.getKeyManagers(), null, null);
    return context;
  }

  /** Counts the connections the server accepted and saw closed. */
  private final class SessionCounter implements IOSessionListener {

    @Override
    public void connected(final IOSession session) {
      connectedSessions.incrementAndGet();
    }

    @Override
    public void startTls(final IOSession session) {}

    @Override
    public void inputReady(final IOSession session) {}

    @Override
    public void outputReady(final IOSession session) {}

    @Override
    public void timeout(final IOSession session) {}

    @Override
    public void exception(final IOSession session, final Exception cause) {}

    @Override
    public void disconnected(final IOSession session) {
      disconnectedSessions.incrementAndGet();
    }
  }

  /** Accepts every request and never answers. */
  private final class SilentHandler
      implements AsyncServerRequestHandler<Message<HttpRequest, Void>> {

    @Override
    public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
        final HttpRequest request, final EntityDetails entityDetails, final HttpContext context) {
      receivedRequests.incrementAndGet();
      return new BasicRequestConsumer<>(new DiscardingEntityConsumer<>());
    }

    @Override
    public void handle(
        final Message<HttpRequest, Void> request,
        final ResponseTrigger responseTrigger,
        final HttpContext context) {
      // intentionally never respond
    }
  }
}
