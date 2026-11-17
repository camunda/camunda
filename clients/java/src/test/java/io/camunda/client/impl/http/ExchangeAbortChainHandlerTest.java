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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Test;

class ExchangeAbortChainHandlerTest {

  private final ExchangeAbortChainHandler handler = new ExchangeAbortChainHandler();
  private final AsyncExecRuntime execRuntime = mock(AsyncExecRuntime.class);
  private final AsyncExecChain chain = mock(AsyncExecChain.class);
  private final AsyncExecCallback callback = mock(AsyncExecCallback.class);
  private final HttpRequest request = new BasicHttpRequest("GET", "/v2/documents/document-id");
  private final HttpClientContext context = HttpClientContext.create();

  @Test
  void shouldDiscardConnectionWhenExchangeIsAborted() throws Exception {
    // given
    handler.execute(request, null, scope(), chain, callback);

    // when
    ExchangeAbortChainHandler.abort(context);

    // then
    verify(execRuntime).discardEndpoint();
  }

  @Test
  void shouldDoNothingWhenRequestDidNotGoThroughHandler() {
    // when / then
    assertThatCode(() -> ExchangeAbortChainHandler.abort(context)).doesNotThrowAnyException();
    assertThat(ExchangeAbortChainHandler.isAborted(context)).isFalse();
  }

  @Test
  void shouldNotReportExchangeAsAbortedUntilItIsAborted() throws Exception {
    // when
    handler.execute(request, null, scope(), chain, callback);

    // then
    assertThat(ExchangeAbortChainHandler.isAborted(context)).isFalse();
  }

  @Test
  void shouldReportExchangeAsAbortedBeforeDiscardingConnection() throws Exception {
    // given the I/O thread can fail on the closed connection and ask whether to retry before abort
    // returns, so the exchange must already count as aborted by the time the connection closes
    handler.execute(request, null, scope(), chain, callback);
    final AtomicBoolean abortedWhenDiscarding = new AtomicBoolean();
    doAnswer(
            invocation -> {
              abortedWhenDiscarding.set(ExchangeAbortChainHandler.isAborted(context));
              return null;
            })
        .when(execRuntime)
        .discardEndpoint();

    // when
    ExchangeAbortChainHandler.abort(context);

    // then
    assertThat(abortedWhenDiscarding).isTrue();
  }

  @Test
  void shouldNotFailWhenDiscardingConnectionFails() throws Exception {
    // given
    handler.execute(request, null, scope(), chain, callback);
    doThrow(new IllegalStateException("endpoint not acquired")).when(execRuntime).discardEndpoint();

    // when / then
    assertThatCode(() -> ExchangeAbortChainHandler.abort(context)).doesNotThrowAnyException();
    verify(execRuntime).discardEndpoint();
    assertThat(ExchangeAbortChainHandler.isAborted(context)).isTrue();
  }

  private Scope scope() {
    return new Scope(
        "ex-" + System.nanoTime(),
        new HttpRoute(new HttpHost("localhost", 8080)),
        request,
        mock(CancellableDependency.class),
        context,
        execRuntime);
  }
}
