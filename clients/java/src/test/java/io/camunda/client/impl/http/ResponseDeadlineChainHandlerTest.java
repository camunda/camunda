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
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResponseDeadlineChainHandlerTest {

  private static final Duration MARGIN = Duration.ofMillis(100);

  private final ResponseDeadlineChainHandler handler = new ResponseDeadlineChainHandler(MARGIN);
  private final AsyncExecRuntime execRuntime = mock(AsyncExecRuntime.class);
  private final CancellableDependency cancellableDependency = mock(CancellableDependency.class);
  private final AsyncExecCallback callback = mock(AsyncExecCallback.class);
  private final AsyncExecChain chain = mock(AsyncExecChain.class);
  private final HttpRequest request = new BasicHttpRequest("POST", "/v2/jobs/activation");

  @Test
  void shouldDiscardEndpointAndFailWhenNoResponseArrives() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));

    // when
    handler.execute(request, null, scope, chain, callback);

    // then
    final ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
    await().untilAsserted(() -> verify(callback).failed(failure.capture()));
    assertThat(failure.getValue()).isInstanceOf(SocketTimeoutException.class);
    verify(execRuntime).discardEndpoint();
  }

  @Test
  void shouldNotInterfereWhenResponseArrivesInTime() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    final AtomicReference<AsyncExecCallback> exchange = proceed(scope, null);

    // when
    exchange.get().handleResponse(mock(HttpResponse.class), null);
    exchange.get().completed();
    await().during(Duration.ofMillis(400)).untilAsserted(() -> {});

    // then
    verify(callback).completed();
    verify(callback, never()).failed(any());
    verify(execRuntime, never()).discardEndpoint();
  }

  @Test
  void shouldNotReportTimeoutAfterTransportFailure() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    final AtomicReference<AsyncExecCallback> exchange = proceed(scope, null);
    final IOException cause = new IOException("connection reset");

    // when
    exchange.get().failed(cause);
    await().during(Duration.ofMillis(400)).untilAsserted(() -> {});

    // then
    verify(callback).failed(eq(cause));
    verify(execRuntime, never()).discardEndpoint();
  }

  @Test
  void shouldGiveEachExchangeItsOwnDeadline() throws Exception {
    // given
    final Scope first = scope(Timeout.ofMilliseconds(100));
    final Scope second = scope(Timeout.ofMilliseconds(100));
    final AsyncExecCallback callbackOfSecond = mock(AsyncExecCallback.class);

    // when
    handler.execute(request, null, first, chain, callback);
    handler.execute(request, null, second, chain, callbackOfSecond);

    // then
    await().untilAsserted(() -> verify(callback).failed(any(SocketTimeoutException.class)));
    await().untilAsserted(() -> verify(callbackOfSecond).failed(any(SocketTimeoutException.class)));
  }

  @Test
  void shouldCancelExchangeWhenDeadlineExpires() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));

    // when
    handler.execute(request, null, scope, chain, callback);

    // then
    await().untilAsserted(() -> verify(cancellableDependency).cancel());
  }

  @Test
  void shouldReleaseConnectionOfCancelledExchangeWithoutReportingFailure() throws Exception {
    // given the caller gave up, e.g. by closing a job worker, but the connection is still leased
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    when(cancellableDependency.isCancelled()).thenReturn(true);

    // when
    handler.execute(request, null, scope, chain, callback);

    // then
    await().untilAsserted(() -> verify(execRuntime).discardEndpoint());
    verify(callback, never()).failed(any());
  }

  @Test
  void shouldRejectResponseThatArrivesAfterDeadline() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    final AtomicReference<AsyncExecCallback> exchange = proceed(scope, null);
    await().untilAsserted(() -> verify(callback).failed(any(SocketTimeoutException.class)));

    // when / then
    assertThatThrownBy(() -> exchange.get().handleResponse(mock(HttpResponse.class), null))
        .isInstanceOf(IOException.class);
    verify(callback, never()).handleResponse(any(), any());
  }

  @Test
  void shouldNotReportOutcomeOfExchangeAfterDeadline() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    final AtomicReference<AsyncExecCallback> exchange = proceed(scope, null);
    await().untilAsserted(() -> verify(callback).failed(any(SocketTimeoutException.class)));

    // when
    exchange.get().completed();
    exchange.get().failed(new IOException("late failure"));

    // then
    verify(callback, never()).completed();
    verify(callback).failed(any());
  }

  @Test
  void shouldCancelExchangeEvenWhenReportingTheFailureThrows() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    doThrow(new IllegalStateException("boom")).when(callback).failed(any());

    // when
    handler.execute(request, null, scope, chain, callback);

    // then
    await().untilAsserted(() -> verify(cancellableDependency).cancel());
  }

  @Test
  void shouldNotFailWhenContextHasNoRequestConfig() throws Exception {
    // given
    final Scope scope =
        new Scope(
            "ex-without-config",
            new HttpRoute(new HttpHost("localhost", 8080)),
            request,
            cancellableDependency,
            HttpClientContext.create(),
            execRuntime);

    // when
    handler.execute(request, null, scope, chain, callback);

    // then
    verify(chain).proceed(eq(request), any(), eq(scope), any());
  }

  @Test
  void shouldNotBoundRequestWithoutResponseTimeout() throws Exception {
    // given
    final Scope scope = scope(Timeout.DISABLED);

    // when
    handler.execute(request, null, scope, chain, callback);
    await().during(Duration.ofMillis(300)).untilAsserted(() -> {});

    // then
    verify(chain).proceed(request, null, scope, callback);
    verify(callback, never()).failed(any());
  }

  @Test
  void shouldNotCutOffBodyThatIsStillBeingSent() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    final AsyncEntityProducer upload = uploadThatEndsItsStream();

    // when the body takes much longer than response timeout plus margin
    handler.execute(request, upload, scope, chain, callback);
    await().during(Duration.ofMillis(500)).untilAsserted(() -> {});

    // then
    verify(callback, never()).failed(any());
    verify(execRuntime, never()).discardEndpoint();
  }

  @Test
  void shouldStartDeadlineWhenBodyHasBeenSent() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    final AsyncEntityProducer upload = uploadThatEndsItsStream();
    handler.execute(request, upload, scope, chain, callback);
    final ArgumentCaptor<AsyncEntityProducer> sent =
        ArgumentCaptor.forClass(AsyncEntityProducer.class);
    verify(chain).proceed(eq(request), sent.capture(), eq(scope), any());

    // when
    sent.getValue().produce(mock(DataStreamChannel.class));

    // then
    await().untilAsserted(() -> verify(callback).failed(any(SocketTimeoutException.class)));
    verify(execRuntime).discardEndpoint();
  }

  @Test
  void shouldNotStartDeadlineWhenExchangeFailedBeforeBodyWasSent() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    final AsyncEntityProducer upload = uploadThatEndsItsStream();
    final AtomicReference<AsyncExecCallback> exchange = proceed(scope, upload);
    final ArgumentCaptor<AsyncEntityProducer> sent =
        ArgumentCaptor.forClass(AsyncEntityProducer.class);
    verify(chain).proceed(eq(request), sent.capture(), eq(scope), any());
    final IOException cause = new IOException("connection refused");

    // when
    exchange.get().failed(cause);
    sent.getValue().produce(mock(DataStreamChannel.class));
    await().during(Duration.ofMillis(400)).untilAsserted(() -> {});

    // then
    verify(callback).failed(eq(cause));
    verify(execRuntime, never()).discardEndpoint();
  }

  @Test
  void shouldDescribeBodyLikeTheWrappedProducer() throws Exception {
    // given
    final Scope scope = scope(Timeout.ofMilliseconds(100));
    final AsyncEntityProducer upload = uploadThatEndsItsStream();
    when(upload.getContentLength()).thenReturn(42L);
    when(upload.getContentType()).thenReturn("application/json");
    when(upload.isRepeatable()).thenReturn(true);

    // when
    handler.execute(request, upload, scope, chain, callback);

    // then
    final ArgumentCaptor<AsyncEntityProducer> sent =
        ArgumentCaptor.forClass(AsyncEntityProducer.class);
    verify(chain).proceed(eq(request), sent.capture(), eq(scope), any());
    assertThat(sent.getValue().getContentLength()).isEqualTo(42L);
    assertThat(sent.getValue().getContentType()).isEqualTo("application/json");
    assertThat(sent.getValue().isRepeatable()).isTrue();
  }

  private static AsyncEntityProducer uploadThatEndsItsStream() throws IOException {
    final AsyncEntityProducer upload = mock(AsyncEntityProducer.class);
    doAnswer(
            invocation -> {
              invocation.<DataStreamChannel>getArgument(0).endStream();
              return null;
            })
        .when(upload)
        .produce(any());
    return upload;
  }

  private AtomicReference<AsyncExecCallback> proceed(
      final Scope scope, final AsyncEntityProducer producer) throws Exception {
    final AtomicReference<AsyncExecCallback> wrapped = new AtomicReference<AsyncExecCallback>();
    doAnswer(
            invocation -> {
              wrapped.set(invocation.getArgument(3));
              return null;
            })
        .when(chain)
        .proceed(any(), any(), any(), any());
    handler.execute(request, producer, scope, chain, callback);
    return wrapped;
  }

  private Scope scope(final Timeout responseTimeout) {
    final HttpClientContext context = HttpClientContext.create();
    context.setRequestConfig(RequestConfig.custom().setResponseTimeout(responseTimeout).build());
    return new Scope(
        "ex-" + System.nanoTime(),
        new HttpRoute(new HttpHost("localhost", 8080)),
        request,
        cancellableDependency,
        context,
        execRuntime);
  }
}
