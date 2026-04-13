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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CredentialsProvider;
import java.net.URI;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class HttpClientTest {

  private CloseableHttpAsyncClient mockApacheClient;
  private ModalCloseable mockConnectionManager;
  private HttpClient httpClient;

  @BeforeEach
  void setUp() {
    mockApacheClient = mock(CloseableHttpAsyncClient.class);
    mockConnectionManager = mock(ModalCloseable.class);

    httpClient =
        new HttpClient(
            mockApacheClient,
            mockConnectionManager,
            new ObjectMapper(),
            URI.create("http://localhost:12345/v2"),
            RequestConfig.custom().build(),
            1024 * 1024,
            TimeValue.ofMilliseconds(100),
            mock(CredentialsProvider.class));
  }

  @Test
  void shouldCloseConnectionManagerWithImmediateModeOnClose() throws Exception {
    // when
    httpClient.close();

    // then
    verify(mockConnectionManager).close(CloseMode.IMMEDIATE);
  }

  @Test
  void shouldCloseConnectionManagerBeforeInitiatingGracefulShutdown() throws Exception {
    // given
    final InOrder inOrder = inOrder(mockConnectionManager, mockApacheClient);

    // when
    httpClient.close();

    // then - connection pool is force-closed before the reactor starts graceful shutdown,
    // so idle connections don't block awaitShutdown
    inOrder.verify(mockConnectionManager).close(CloseMode.IMMEDIATE);
    inOrder.verify(mockApacheClient).close(CloseMode.GRACEFUL);
  }
}
