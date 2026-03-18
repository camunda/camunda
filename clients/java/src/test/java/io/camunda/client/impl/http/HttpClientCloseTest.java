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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.command.ClientException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpClientCloseTest {

  private CloseableHttpAsyncClient apacheClient;
  private HttpClient httpClient;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    apacheClient = mock(CloseableHttpAsyncClient.class);
    when(apacheClient.execute(
            any(AsyncRequestProducer.class),
            any(AsyncResponseConsumer.class),
            any(FutureCallback.class)))
        .thenReturn(mock(Future.class));

    httpClient =
        new HttpClient(
            apacheClient,
            new ObjectMapper(),
            URI.create("http://localhost:8080/v2"),
            RequestConfig.DEFAULT,
            4 * 1024 * 1024,
            TimeValue.ofSeconds(1),
            mock(CredentialsProvider.class));
    httpClient.start();
  }

  @Test
  void shouldCancelPendingFuturesOnClose() throws Exception {
    final HttpCamundaFuture<Void> future = new HttpCamundaFuture<>();

    httpClient.post("/jobs/activation", "{}", RequestConfig.DEFAULT, resp -> null, future);

    assertThat(future.isDone()).isFalse();

    httpClient.close();

    assertThat(future.isCancelled()).isTrue();
  }

  @Test
  void shouldBeIdempotentOnDoubleClose() throws Exception {
    httpClient.close();
    httpClient.close(); // should not throw
  }

  @Test
  void shouldRejectRequestsAfterClose() throws Exception {
    httpClient.close();

    final HttpCamundaFuture<Void> future = new HttpCamundaFuture<>();
    httpClient.post("/jobs/activation", "{}", RequestConfig.DEFAULT, resp -> null, future);

    assertThat(future.isCompletedExceptionally()).isTrue();
    assertThatThrownBy(future::get)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(ClientException.class)
        .hasMessageContaining("Client is closed");
  }
}
