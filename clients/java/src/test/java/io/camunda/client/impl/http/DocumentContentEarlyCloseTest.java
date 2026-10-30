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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

  private static final int MAX_MESSAGE_SIZE = 64 * 1024;
  private static final int CHUNK_SIZE = 8 * 1024;

  /**
   * Far more than the socket buffers on both sides hold, so the server is still sending when the
   * stream is closed.
   */
  private static final long DOCUMENT_SIZE = 64L * 1024 * 1024;

  /** Long enough that a timeout cannot be what ends the download within the test. */
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

  private static ProxySelector originalProxySelector;

  private final byte[] chunk = new byte[CHUNK_SIZE];
  private final AtomicLong bodyBytesSent = new AtomicLong();
  private final AtomicBoolean connectionDropped = new AtomicBoolean();
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

  private void startServer(final BodyFraming framing) throws Exception {
    ThreadLocalRandom.current().nextBytes(chunk);
    serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    final Thread server = new Thread(() -> serveDocument(framing), "endless-document-server");
    server.setDaemon(true);
    server.start();

    client =
        CamundaClient.newClientBuilder()
            .applyEnvironmentVariableOverrides(false)
            .preferRestOverGrpc(true)
            .restAddress(new URI("http://localhost:" + serverSocket.getLocalPort()))
            .maxMessageSize(MAX_MESSAGE_SIZE)
            .build();
  }

  @AfterEach
  void tearDown() throws IOException {
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
    startServer(framing);
    final InputStream content =
        client
            .newDocumentContentGetRequest("document-id")
            .requestTimeout(REQUEST_TIMEOUT)
            .send()
            .get(20, TimeUnit.SECONDS);
    // the client buffers more than its capacity, so reading a few bytes grants the server nothing
    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> bodyBytesSent.get() >= MAX_MESSAGE_SIZE + 2 * CHUNK_SIZE);
    assertThat(content.read(new byte[16])).isPositive();

    // when
    content.close();

    // then
    await("the client drops the connection")
        .atMost(Duration.ofSeconds(10))
        .untilTrue(connectionDropped);
    assertThat(bodyBytesSent.get()).isLessThan(DOCUMENT_SIZE);
  }

  /** Sends the document as fast as the client accepts it, until the client drops the connection. */
  private void serveDocument(final BodyFraming framing) {
    try (final Socket socket = serverSocket.accept()) {
      final InputStream request = socket.getInputStream();
      final ByteArrayOutputStream head = new ByteArrayOutputStream();
      while (!head.toString("ISO-8859-1").endsWith("\r\n\r\n")) {
        final int b = request.read();
        if (b < 0) {
          return;
        }
        head.write(b);
      }

      final OutputStream out = socket.getOutputStream();
      try {
        out.write(framing.responseHead().getBytes(StandardCharsets.ISO_8859_1));
        while (bodyBytesSent.get() < DOCUMENT_SIZE) {
          framing.writeChunk(out, chunk);
          bodyBytesSent.addAndGet(CHUNK_SIZE);
        }
        framing.writeEnd(out);
        out.flush();
      } catch (final IOException e) {
        connectionDropped.set(true);
      }
    } catch (final IOException e) {
      // the server socket is closed when the test finishes
    }
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
