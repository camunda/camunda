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
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.nio.support.BasicServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Like {@link DocumentContentEarlyCloseTest}, but over HTTP/2. There the server may only send as
 * much as the client allows, and once the client holds about {@code maxMessageSize} of unread data,
 * it allows nothing more. No timeout ends a download that waits like this (#66418), so closing the
 * stream must end the download, or it hangs forever.
 */
class DocumentContentEarlyCloseHttp2Test {

  private static final String DOCUMENT_ID = "document-id";

  /** A small document that the server sends in full. */
  private static final String SMALL_DOCUMENT_ID = "small-document-id";

  private static final String SMALL_DOCUMENT_CONTENT = "small document";
  private static final int MAX_MESSAGE_SIZE = 64 * 1024;
  private static final int CHUNK_SIZE = 8 * 1024;
  private static final long DOCUMENT_SIZE = 64L * 1024 * 1024;

  /** Long enough that a timeout cannot be what ends the download within the test. */
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

  private static ProxySelector originalProxySelector;

  @TempDir Path tempDir;

  private final byte[] chunk = new byte[CHUNK_SIZE];
  private final AtomicReference<ProtocolVersion> requestVersion = new AtomicReference<>();
  private final AtomicLong bodyBytesSent = new AtomicLong();
  private final AtomicBoolean responseFailed = new AtomicBoolean();
  private final List<String> requestedDocuments = new CopyOnWriteArrayList<>();

  /** The exchange of the first request, which is the one for the document. */
  private final AtomicReference<AsyncExecChain.Scope> documentExchange = new AtomicReference<>();

  /**
   * How much the server sends; it ends the response only if this is the whole document, and goes
   * silent otherwise.
   */
  private volatile long sendLimit = DOCUMENT_SIZE;

  private HttpAsyncServer server;
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
    ThreadLocalRandom.current().nextBytes(chunk);
    final SelfSignedCertificate certificate = SelfSignedCertificate.create(tempDir);

    server =
        H2ServerBootstrap.bootstrap()
            .setTlsStrategy(new H2ServerTlsStrategy(certificate.serverSslContext()))
            .setRequestRouter(
                (request, context) -> () -> new BasicServerExchangeHandler<>(new DocumentHandler()))
            .create();
    server.start();
    final ListenerEndpoint endpoint =
        server.listen(new InetSocketAddress("localhost", 0), URIScheme.HTTPS).get();
    final int port = ((InetSocketAddress) endpoint.getAddress()).getPort();

    client =
        CamundaClient.newClientBuilder()
            .applyEnvironmentVariableOverrides(false)
            .preferRestOverGrpc(true)
            .restAddress(new URI("https://localhost:" + port))
            .caCertificatePath(certificate.certificatePath().toString())
            .withChainHandlers(
                (request, entityProducer, scope, chain, callback) -> {
                  documentExchange.compareAndSet(null, scope);
                  chain.proceed(request, entityProducer, scope, callback);
                })
            .maxMessageSize(MAX_MESSAGE_SIZE)
            .build();
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
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void shouldEndDownloadWhenStreamIsClosedBeforeEndOfDocument() throws Exception {
    // given
    final InputStream content =
        client
            .newDocumentContentGetRequest(DOCUMENT_ID)
            .requestTimeout(REQUEST_TIMEOUT)
            .send()
            .get(20, TimeUnit.SECONDS);
    assertThat(requestVersion.get()).isEqualTo(HttpVersion.HTTP_2);
    // the client holds more than it buffers, so reading a few bytes allows the server nothing more
    await().atMost(Duration.ofSeconds(20)).until(() -> bodyBytesSent.get() > MAX_MESSAGE_SIZE);
    assertThat(content.read(new byte[16])).isPositive();

    // when
    content.close();

    // then
    await("the client ends the download").atMost(Duration.ofSeconds(10)).untilTrue(responseFailed);
    assertThat(bodyBytesSent.get()).isLessThan(DOCUMENT_SIZE);
    // the client still works, and did not ask for the document again
    try (final InputStream smallDocument =
        client.newDocumentContentGetRequest(SMALL_DOCUMENT_ID).send().get(20, TimeUnit.SECONDS)) {
      assertThat(smallDocument).hasContent(SMALL_DOCUMENT_CONTENT);
    }
    // httpclient5 counts a new try once it learns that the download failed, which happens after
    // the connection is closed, but before the small document is back
    assertThat(documentExchange.get().execCount).as("attempts to get the document").hasValue(1);
    assertThat(requestedDocuments).containsExactly(DOCUMENT_ID, SMALL_DOCUMENT_ID);
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void shouldEndDownloadWhenStreamIsClosedWhileServerIsSilent() throws Exception {
    // given, less than the client buffers, so closing the stream cannot make the server send more
    sendLimit = 2 * CHUNK_SIZE;
    final InputStream content =
        client
            .newDocumentContentGetRequest(DOCUMENT_ID)
            .requestTimeout(REQUEST_TIMEOUT)
            .send()
            .get(20, TimeUnit.SECONDS);
    assertThat(requestVersion.get()).isEqualTo(HttpVersion.HTTP_2);
    new DataInputStream(content).readFully(new byte[(int) sendLimit]);

    // when
    content.close();

    // then
    await("the client ends the download").atMost(Duration.ofSeconds(10)).untilTrue(responseFailed);
    // the client still works, and did not ask for the document again
    try (final InputStream smallDocument =
        client.newDocumentContentGetRequest(SMALL_DOCUMENT_ID).send().get(20, TimeUnit.SECONDS)) {
      assertThat(smallDocument).hasContent(SMALL_DOCUMENT_CONTENT);
    }
    // httpclient5 counts a new try once it learns that the download failed, which happens after
    // the connection is closed, but before the small document is back
    assertThat(documentExchange.get().execCount).as("attempts to get the document").hasValue(1);
    assertThat(requestedDocuments).containsExactly(DOCUMENT_ID, SMALL_DOCUMENT_ID);
  }

  /** Answers a request for the small document with it, and any other request with the document. */
  private final class DocumentHandler
      implements AsyncServerRequestHandler<Message<HttpRequest, Void>> {

    @Override
    public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
        final HttpRequest request, final EntityDetails entityDetails, final HttpContext context) {
      requestVersion.set(request.getVersion());
      requestedDocuments.add(documentId(request));
      return new BasicRequestConsumer<>(new DiscardingEntityConsumer<>());
    }

    @Override
    public void handle(
        final Message<HttpRequest, Void> request,
        final ResponseTrigger responseTrigger,
        final HttpContext context)
        throws HttpException, IOException {
      final AsyncEntityProducer document =
          SMALL_DOCUMENT_ID.equals(documentId(request.getHead()))
              ? AsyncEntityProducers.create(
                  SMALL_DOCUMENT_CONTENT, ContentType.APPLICATION_OCTET_STREAM)
              : new EndlessDocumentProducer();
      responseTrigger.submitResponse(
          new BasicResponseProducer(new BasicHttpResponse(HttpStatus.SC_OK), document), context);
    }

    private String documentId(final HttpRequest request) {
      final String path = request.getPath().split("\\?")[0];
      return path.substring(path.lastIndexOf('/') + 1);
    }
  }

  /**
   * Sends the document as fast as the client allows, up to {@link #sendLimit}, until the client
   * ends the download.
   */
  private final class EndlessDocumentProducer implements AsyncEntityProducer {

    private final ByteBuffer pending = ByteBuffer.wrap(chunk);

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
      while (bodyBytesSent.get() < sendLimit) {
        if (!pending.hasRemaining()) {
          pending.rewind();
        }
        final int written = channel.write(pending);
        if (written == 0) {
          // httpcore5 calls produce again once it can send more
          return;
        }
        bodyBytesSent.addAndGet(written);
      }
      if (sendLimit == DOCUMENT_SIZE) {
        channel.endStream();
      }
    }

    @Override
    public int available() {
      return (int) Math.min(sendLimit - bodyBytesSent.get(), Integer.MAX_VALUE);
    }

    @Override
    public void failed(final Exception cause) {
      responseFailed.set(true);
    }

    @Override
    public void releaseResources() {}

    @Override
    public boolean isRepeatable() {
      return false;
    }

    @Override
    public long getContentLength() {
      return -1;
    }

    @Override
    public String getContentType() {
      return "application/octet-stream";
    }

    @Override
    public String getContentEncoding() {
      return null;
    }

    @Override
    public boolean isChunked() {
      return false;
    }

    @Override
    public Set<String> getTrailerNames() {
      return Collections.emptySet();
    }
  }
}
