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
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.AsyncResponseBuilder;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for camunda/camunda#63666. A client that sends requests more often than the idle
 * eviction interval used to keep one pooled connection forever, so a connection to a replaced
 * backend pod was never recycled. The connection lifetime bounds that, independent of activity.
 */
class HttpClientFactoryTest {

  private static final String TOPOLOGY_RESPONSE =
      "{\"brokers\":[],\"clusterSize\":1,\"partitionsCount\":1,\"replicationFactor\":1,"
          + "\"gatewayVersion\":\"test\"}";

  private static ProxySelector originalProxySelector;

  @TempDir Path tempDir;

  private final AtomicInteger connectedSessions = new AtomicInteger();
  private PoolingAsyncClientConnectionManager pool;
  private HttpAsyncServer server;
  private int port;
  private Path certificatePath;

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
    final SelfSignedCertificate certificate = SelfSignedCertificate.create(tempDir);
    certificatePath = certificate.certificatePath();

    server =
        H2ServerBootstrap.bootstrap()
            .setTlsStrategy(new H2ServerTlsStrategy(certificate.serverSslContext()))
            .setIOSessionListener(new SessionCounter())
            .setRequestRouter(
                (request, context) -> () -> new BasicServerExchangeHandler<>(new OkHandler()))
            .create();
    server.start();
    final ListenerEndpoint endpoint =
        server.listen(new InetSocketAddress("localhost", 0), URIScheme.HTTPS).get();
    port = ((InetSocketAddress) endpoint.getAddress()).getPort();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.close(CloseMode.IMMEDIATE);
    }
  }

  @Test
  void shouldOpenNewConnectionOnceConnectionLifetimeElapsed() throws Exception {
    // given
    final Duration timeToLive = Duration.ofSeconds(1);
    try (final CamundaClient client =
        buildClient(TimeValue.ofMilliseconds(timeToLive.toMillis()))) {
      client.newTopologyRequest().send().join();
      assertThat(connectedSessions.get()).isEqualTo(1);

      // when the pooled connection outlives its lifetime, even though it was used recently
      await().pollDelay(timeToLive.plusMillis(500)).until(() -> true);
      client.newTopologyRequest().send().join();

      // then the next request is sent over a new connection
      assertThat(connectedSessions.get()).isEqualTo(2);
    }
  }

  @Test
  void shouldReuseConnectionWithinConnectionLifetime() throws Exception {
    // given
    try (final CamundaClient client = buildClient(TimeValue.ofMinutes(5))) {

      // when, waiting until the connection is back in the pool before the next request
      for (int i = 0; i < 3; i++) {
        client.newTopologyRequest().send().join();
        await().until(() -> pool.getTotalStats().getLeased() == 0);
      }

      // then
      assertThat(connectedSessions.get()).isEqualTo(1);
    }
  }

  private CamundaClient buildClient(final TimeValue connectionTimeToLive) throws Exception {
    final CamundaClientBuilderImpl configuration =
        (CamundaClientBuilderImpl)
            CamundaClient.newClientBuilder()
                .applyEnvironmentVariableOverrides(false)
                .preferRestOverGrpc(true)
                .restAddress(new URI("https://localhost:" + port))
                .caCertificatePath(certificatePath.toString());
    final HttpClientFactory factory =
        new HttpClientFactory(
            configuration, ResponseDeadlineChainHandler.DEFAULT_MARGIN, connectionTimeToLive);
    pool = factory.createConnectionManager();
    return new CamundaClientImpl(
        configuration, CamundaClientImpl.buildChannel(configuration), factory.createClient(pool));
  }

  /** Counts the connections the server accepted. */
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
    public void disconnected(final IOSession session) {}
  }

  /** Answers every request with an empty topology. */
  private static final class OkHandler
      implements AsyncServerRequestHandler<Message<HttpRequest, Void>> {

    @Override
    public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
        final HttpRequest request, final EntityDetails entityDetails, final HttpContext context) {
      return new BasicRequestConsumer<>(new DiscardingEntityConsumer<>());
    }

    @Override
    public void handle(
        final Message<HttpRequest, Void> request,
        final ResponseTrigger responseTrigger,
        final HttpContext context)
        throws HttpException, IOException {
      responseTrigger.submitResponse(
          AsyncResponseBuilder.create(HttpStatus.SC_OK)
              .setEntity(TOPOLOGY_RESPONSE, ContentType.APPLICATION_JSON)
              .build(),
          context);
    }
  }
}
