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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Goes through the real client path: when the server closes the connection in the middle of a
 * chunked document download, the HTTP client releases the response consumer directly (without
 * reaching {@link DocumentDataConsumer#failed}) and a reader blocked on the document stream must be
 * woken up with an error instead of waiting forever.
 */
class DocumentContentConnectionClosedTest {

  private static ProxySelector originalProxySelector;

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
    serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    final Thread server = new Thread(this::serveTruncatedDocument, "truncated-document-server");
    server.setDaemon(true);
    server.start();

    client =
        CamundaClient.newClientBuilder()
            .applyEnvironmentVariableOverrides(false)
            .preferRestOverGrpc(true)
            .restAddress(new URI("http://localhost:" + serverSocket.getLocalPort()))
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
  void shouldFailReaderWhenConnectionIsClosedInTheMiddleOfTheResponseBody() throws Exception {
    // given
    final InputStream document =
        client.newDocumentContentGetRequest("document-id").send().get(20, TimeUnit.SECONDS);

    // when
    final CompletableFuture<byte[]> read =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                final byte[] buffer = new byte[1024];
                while (document.read(buffer) >= 0) {
                  // drain the document until the end of the stream or an error
                }
                return buffer;
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            });

    // then
    assertThatThrownBy(() -> read.get(30, TimeUnit.SECONDS))
        .hasRootCauseInstanceOf(IOException.class);
    // the failure is sticky, so reading again fails again instead of blocking
    assertThatThrownBy(document::read).isInstanceOf(IOException.class);
  }

  /** Sends the response headers and a part of a chunked body, then closes the connection. */
  private void serveTruncatedDocument() {
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
                  + "\r\n"
                  + "5\r\nhello\r\n")
              .getBytes(StandardCharsets.ISO_8859_1));
      out.flush();
      // closing without the terminating zero-length chunk
    } catch (final IOException e) {
      // the server socket is closed when the test finishes
    }
  }
}
