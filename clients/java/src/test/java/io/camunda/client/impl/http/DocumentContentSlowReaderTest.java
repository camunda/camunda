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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Goes through the real client path: a document several times larger than the client's buffer
 * ({@code maxMessageSize}) must stream completely to a reader that is slower than the network. The
 * HTTP I/O layer keeps handing response data to the client even when that is more than the space
 * the client has asked for, so a full buffer must not break the download.
 */
class DocumentContentSlowReaderTest {

  private static final int MAX_MESSAGE_SIZE = 64 * 1024;
  private static final int DOCUMENT_SIZE = 16 * MAX_MESSAGE_SIZE;
  private static final int CHUNK_SIZE = 8 * 1024;

  /**
   * Much smaller than the pieces the I/O layer hands over, so one read never makes room for one.
   */
  private static final int READ_SIZE = 1024;

  private static ProxySelector originalProxySelector;

  private final byte[] document = new byte[DOCUMENT_SIZE];
  private final AtomicLong bodyBytesSent = new AtomicLong();
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

  @BeforeEach
  void setUp() throws Exception {
    ThreadLocalRandom.current().nextBytes(document);
    serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    final Thread server = new Thread(this::serveDocument, "large-document-server");
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

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void shouldStreamDocumentLargerThanBufferToReaderSlowerThanNetwork() throws Exception {
    // given
    final InputStream content =
        client.newDocumentContentGetRequest("document-id").send().get(20, TimeUnit.SECONDS);
    // hold the reader back until the server has sent more than the client can buffer
    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> bodyBytesSent.get() >= MAX_MESSAGE_SIZE + 2 * CHUNK_SIZE);

    // when
    final byte[] received = readInSmallPieces(content);

    // then
    assertThat(received).hasSize(DOCUMENT_SIZE).isEqualTo(document);
  }

  private static byte[] readInSmallPieces(final InputStream content) throws IOException {
    final ByteArrayOutputStream received = new ByteArrayOutputStream(DOCUMENT_SIZE);
    final byte[] piece = new byte[READ_SIZE];
    int read;
    while ((read = content.read(piece)) >= 0) {
      received.write(piece, 0, read);
    }
    return received.toByteArray();
  }

  /** Sends the document as a chunked response body, as fast as the client accepts it. */
  private void serveDocument() {
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
      out.write(
          ("HTTP/1.1 200 OK\r\n"
                  + "Content-Type: application/octet-stream\r\n"
                  + "Transfer-Encoding: chunked\r\n"
                  + "\r\n")
              .getBytes(StandardCharsets.ISO_8859_1));
      for (int offset = 0; offset < DOCUMENT_SIZE; offset += CHUNK_SIZE) {
        final int length = Math.min(CHUNK_SIZE, DOCUMENT_SIZE - offset);
        final ByteArrayOutputStream chunk = new ByteArrayOutputStream(length + 16);
        chunk.write((Integer.toHexString(length) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        chunk.write(document, offset, length);
        chunk.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        out.write(chunk.toByteArray());
        bodyBytesSent.addAndGet(length);
      }
      out.write("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
      out.flush();
    } catch (final IOException e) {
      // the server socket is closed when the test finishes
    }
  }
}
