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
import static org.assertj.core.api.Assumptions.assumeThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A server address that does not answer a connection attempt, for example the address of a deleted
 * pod, blocks a request until the connect timeout has passed. Without a connect timeout
 * httpclient5 waits up to its default of 3 minutes per resolved address, and neither the response
 * timeout nor the response deadline cover the connect. A job worker waiting for such a poll does no
 * work in the meantime.
 */
class HttpClientConnectTimeoutTest {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
  private static final int PROBE_TIMEOUT_MILLIS = 200;

  /** Connections that fill the accept queue of {@link #server}, so that new ones get no answer. */
  private final List<Socket> fillers = new ArrayList<>();

  private ServerSocket server;

  @BeforeEach
  void setUp() throws IOException {
    // a listening socket that never accepts, with the smallest accept queue
    server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    fillAcceptQueue();
  }

  @AfterEach
  void tearDown() throws IOException {
    for (final Socket filler : fillers) {
      filler.close();
    }
    server.close();
  }

  @Test
  @org.junit.jupiter.api.Timeout(30)
  void shouldFailRequestWhenConnectionIsNotEstablishedInTime() throws Exception {
    // given an address that does not answer the connection attempt
    assumeThat(isConnectBlocked())
        .as("the platform drops connections to a full accept queue")
        .isTrue();

    try (final CamundaClient client = buildClient()) {
      // when
      final long start = System.nanoTime();
      assertThatThrownBy(() -> client.newTopologyRequest().send().join()).isNotNull();
      final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

      // then the request fails after the connect timeout instead of waiting for the operating
      // system to give up on the connection attempt (about 127 seconds on Linux)
      assertThat(elapsed).isLessThan(Duration.ofSeconds(20));
    }
  }

  private void fillAcceptQueue() {
    // the first connections are queued, the following ones are dropped on Linux and hang in connect
    for (int i = 0; i < 10; i++) {
      final Socket socket = new Socket();
      try {
        socket.connect(server.getLocalSocketAddress(), PROBE_TIMEOUT_MILLIS);
        fillers.add(socket);
      } catch (final IOException e) {
        closeQuietly(socket);
        return;
      }
    }
  }

  private boolean isConnectBlocked() {
    try (final Socket socket = new Socket()) {
      socket.connect(server.getLocalSocketAddress(), PROBE_TIMEOUT_MILLIS);
      return false;
    } catch (final SocketTimeoutException e) {
      return true;
    } catch (final IOException e) {
      return false;
    }
  }

  private static void closeQuietly(final Socket socket) {
    try {
      socket.close();
    } catch (final IOException ignored) {
      // nothing left to release
    }
  }

  private CamundaClient buildClient() throws Exception {
    final InetSocketAddress address = (InetSocketAddress) server.getLocalSocketAddress();
    final CamundaClientBuilderImpl configuration =
        (CamundaClientBuilderImpl)
            CamundaClient.newClientBuilder()
                .applyEnvironmentVariableOverrides(false)
                .preferRestOverGrpc(true)
                .restAddress(new URI("http://127.0.0.1:" + address.getPort()));
    final HttpClientFactory factory =
        new HttpClientFactory(
            configuration,
            ResponseDeadlineChainHandler.DEFAULT_MARGIN,
            HttpClientFactory.DEFAULT_CONNECTION_TIME_TO_LIVE,
            Timeout.ofMilliseconds(CONNECT_TIMEOUT.toMillis()));
    return new CamundaClientImpl(
        configuration, CamundaClientImpl.buildChannel(configuration), factory.createClient());
  }
}
