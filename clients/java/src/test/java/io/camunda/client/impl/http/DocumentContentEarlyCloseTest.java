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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Goes through the real client path: closing the document stream before the end of the document
 * must end the download. Otherwise the client keeps the connection and a buffer of about {@code
 * maxMessageSize} until the server has sent the whole document or a timeout fails the exchange.
 */
class DocumentContentEarlyCloseTest {

  private static final String DOCUMENT_ID = "document-id";

  /** A small document that the server sends in full. */
  private static final String SMALL_DOCUMENT_ID = "small-document-id";

  private static final String SMALL_DOCUMENT_CONTENT = "small document";
  private static final int MAX_MESSAGE_SIZE = 64 * 1024;
  private static final int CHUNK_SIZE = 8 * 1024;

  /**
   * Far more than the socket buffers on both sides hold, so the server cannot finish the document
   * before the client drops the connection.
   */
  private static final long DOCUMENT_SIZE = 64L * 1024 * 1024;

  /**
   * Where the server waits until the stream is closed. It is past the client's capacity, so reading
   * a few bytes grants the server nothing. Without the pause, the server could send the whole
   * document before the stream is closed, as httpcore5 reads a chunked body beyond the space the
   * client granted for as long as data keeps arriving.
   */
  private static final long PAUSE_AT = MAX_MESSAGE_SIZE + 2 * CHUNK_SIZE;

  /** Long enough that a timeout cannot be what ends the download within the test. */
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

  private static ProxySelector originalProxySelector;

  private final byte[] chunk = new byte[CHUNK_SIZE];
  private final AtomicLong bodyBytesSent = new AtomicLong();
  private final AtomicBoolean connectionDropped = new AtomicBoolean();
  private final CountDownLatch streamClosed = new CountDownLatch(1);
  private final List<String> requestedDocuments = new CopyOnWriteArrayList<>();

  /** The exchange of the first request, which is the one for the document. */
  private final AtomicReference<AsyncExecChain.Scope> documentExchange = new AtomicReference<>();

  private ServerSocket serverSocket;
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

  private void startServer(
      final BodyFraming framing, final long pauseAt, final AfterPause afterPause) throws Exception {
    ThreadLocalRandom.current().nextBytes(chunk);
    serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    final Thread server =
        new Thread(() -> serve(framing, pauseAt, afterPause), "endless-document-server");
    server.setDaemon(true);
    server.start();

    client =
        CamundaClient.newClientBuilder()
            .applyEnvironmentVariableOverrides(false)
            .preferRestOverGrpc(true)
            .restAddress(new URI("http://localhost:" + serverSocket.getLocalPort()))
            .withChainHandlers(
                (request, entityProducer, scope, chain, callback) -> {
                  documentExchange.compareAndSet(null, scope);
                  chain.proceed(request, entityProducer, scope, callback);
                })
            .maxMessageSize(MAX_MESSAGE_SIZE)
            .build();
  }

  @AfterEach
  void tearDown() throws IOException {
    // lets the server go on if the test failed before closing the stream
    streamClosed.countDown();
    if (client != null) {
      client.close();
    }
    if (serverSocket != null) {
      serverSocket.close();
    }
  }

  @ParameterizedTest
  @EnumSource(BodyFraming.class)
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void shouldEndDownloadWhenStreamIsClosedBeforeEndOfDocument(final BodyFraming framing)
      throws Exception {
    // given
    startServer(framing, PAUSE_AT, AfterPause.RESUME_WHEN_CLOSED);
    final InputStream content =
        client
            .newDocumentContentGetRequest(DOCUMENT_ID)
            .requestTimeout(REQUEST_TIMEOUT)
            .send()
            .get(20, TimeUnit.SECONDS);
    await().atMost(Duration.ofSeconds(20)).until(() -> bodyBytesSent.get() >= PAUSE_AT);
    assertThat(content.read(new byte[16])).isPositive();

    // when
    content.close();
    streamClosed.countDown();

    // then
    await("the client drops the connection")
        .atMost(Duration.ofSeconds(10))
        .untilTrue(connectionDropped);
    // httpclient5 counts a new try in the same step that drops the connection
    assertThat(documentExchange.get().execCount).as("attempts to get the document").hasValue(1);
    assertThat(bodyBytesSent.get()).isLessThan(DOCUMENT_SIZE);
    // the client still works, and did not ask for the document again
    try (final InputStream smallDocument =
        client.newDocumentContentGetRequest(SMALL_DOCUMENT_ID).send().get(20, TimeUnit.SECONDS)) {
      assertThat(smallDocument).hasContent(SMALL_DOCUMENT_CONTENT);
    }
    assertThat(requestedDocuments).containsExactly(DOCUMENT_ID, SMALL_DOCUMENT_ID);
  }

  /** Answers the requests one connection at a time, until the test closes the server socket. */
  private void serve(final BodyFraming framing, final long pauseAt, final AfterPause afterPause) {
    try {
      while (true) {
        try (final Socket socket = serverSocket.accept()) {
          final InputStream request = socket.getInputStream();
          final String documentId = readRequestedDocumentId(request);
          if (documentId == null) {
            continue;
          }
          requestedDocuments.add(documentId);
          if (SMALL_DOCUMENT_ID.equals(documentId)) {
            sendSmallDocument(socket.getOutputStream());
          } else {
            sendDocument(request, socket.getOutputStream(), framing, pauseAt, afterPause);
          }
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final IOException e) {
      // the server socket is closed when the test finishes
    }
  }

  /** Returns the ID of the requested document, or null if the client sent no request. */
  private static String readRequestedDocumentId(final InputStream request) throws IOException {
    final ByteArrayOutputStream head = new ByteArrayOutputStream();
    while (!head.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
      final int b = request.read();
      if (b < 0) {
        return null;
      }
      head.write(b);
    }
    final String target = head.toString("ISO-8859-1").split(" ")[1];
    final String path = target.split("\\?")[0];
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private static void sendSmallDocument(final OutputStream out) throws IOException {
    final byte[] content = SMALL_DOCUMENT_CONTENT.getBytes(StandardCharsets.ISO_8859_1);
    out.write(
        ("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Length: "
                + content.length
                + "\r\n"
                + "Connection: close\r\n"
                + "\r\n")
            .getBytes(StandardCharsets.ISO_8859_1));
    out.write(content);
    out.flush();
  }

  /**
   * Sends the document as fast as the client accepts it, pausing once at {@code pauseAt}, and goes
   * on until the client drops the connection.
   */
  private void sendDocument(
      final InputStream request,
      final OutputStream out,
      final BodyFraming framing,
      final long pauseAt,
      final AfterPause afterPause)
      throws InterruptedException {
    try {
      out.write(framing.responseHead().getBytes(StandardCharsets.ISO_8859_1));
      while (bodyBytesSent.get() < DOCUMENT_SIZE) {
        framing.writeChunk(out, chunk);
        if (bodyBytesSent.addAndGet(CHUNK_SIZE) == pauseAt) {
          out.flush();
          if (afterPause == AfterPause.STAY_SILENT) {
            awaitDisconnect(request);
            return;
          }
          streamClosed.await();
        }
      }
      framing.writeEnd(out);
      out.flush();
    } catch (final IOException e) {
      connectionDropped.set(true);
    }
  }

  /** Returns once the client drops the connection; the client sends nothing more until then. */
  private void awaitDisconnect(final InputStream request) {
    try {
      while (request.read() >= 0) {
        // the client sends nothing more after the request
      }
    } catch (final IOException e) {
      // the client reset the connection
    }
    connectionDropped.set(true);
  }

  /** What the server does once it reaches the pause. */
  private enum AfterPause {
    /** Waits until the test closes the stream, then sends the rest of the document. */
    RESUME_WHEN_CLOSED,
    /** Sends nothing more, without ending the response. */
    STAY_SILENT
  }

  /** How the response marks the end of the document; httpcore5 reads the two kinds differently. */
  enum BodyFraming {
    CHUNKED {
      @Override
      String responseHead() {
        return "HTTP/1.1 200 OK\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "\r\n";
      }

      @Override
      void writeChunk(final OutputStream out, final byte[] chunk) throws IOException {
        out.write(
            (Integer.toHexString(chunk.length) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write(chunk);
        out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
      }

      @Override
      void writeEnd(final OutputStream out) throws IOException {
        out.write("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
      }
    },
    CONTENT_LENGTH {
      @Override
      String responseHead() {
        return "HTTP/1.1 200 OK\r\n"
            + "Content-Type: application/octet-stream\r\n"
            + "Content-Length: "
            + DOCUMENT_SIZE
            + "\r\n"
            + "\r\n";
      }

      @Override
      void writeChunk(final OutputStream out, final byte[] chunk) throws IOException {
        out.write(chunk);
      }

      @Override
      void writeEnd(final OutputStream out) {}
    };

    abstract String responseHead();

    abstract void writeChunk(OutputStream out, byte[] chunk) throws IOException;

    abstract void writeEnd(OutputStream out) throws IOException;
  }
}
