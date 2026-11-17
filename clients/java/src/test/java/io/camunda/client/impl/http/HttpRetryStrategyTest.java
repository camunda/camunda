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
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Test;

class HttpRetryStrategyTest {

  private final HttpRetryStrategy retryStrategy = new HttpRetryStrategy();
  private final HttpRequest request = new BasicHttpRequest(Method.GET, "/v2/documents/document-id");

  @Test
  void shouldNotSendRequestAgainWhenDocumentStreamWasClosed() {
    // given
    final IOException failure = new DocumentStreamClosedException();

    // when
    final boolean retry =
        retryStrategy.retryRequest(request, failure, 1, HttpClientContext.create());

    // then
    assertThat(retry).isFalse();
  }

  @Test
  void shouldNotSendRequestAgainWhenDownloadWasAborted() throws Exception {
    // given the stream was closed, and the next read from the closed connection failed
    final HttpClientContext context = HttpClientContext.create();
    new ExchangeAbortChainHandler()
        .execute(
            request,
            null,
            new Scope(
                "ex-aborted",
                new HttpRoute(new HttpHost("localhost", 8080)),
                request,
                mock(CancellableDependency.class),
                context,
                mock(AsyncExecRuntime.class)),
            mock(AsyncExecChain.class),
            mock(AsyncExecCallback.class));
    ExchangeAbortChainHandler.abort(context);
    final IOException failure = new ClosedChannelException();

    // when
    final boolean retry = retryStrategy.retryRequest(request, failure, 1, context);

    // then
    assertThat(retry).isFalse();
  }

  @Test
  void shouldSendRequestAgainWhenServerResetTheStream() {
    // given
    final IOException failure = new HttpStreamResetException("Stream reset by the server");

    // when
    final boolean retry =
        retryStrategy.retryRequest(request, failure, 1, HttpClientContext.create());

    // then
    assertThat(retry).isTrue();
  }
}
