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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces a wall-clock deadline on the time until a response head arrives.
 *
 * <p>Since httpclient5 5.6.1 the configured response timeout is not applied to HTTP/2 connections,
 * so a request that never gets an answer waits forever, keeps its connection leased, and stalls
 * e.g. a job worker's poll loop. This handler bounds such a request by the effective response
 * timeout plus a margin. When the deadline passes, it discards the connection, which releases the
 * lease, and fails the exchange with a {@link SocketTimeoutException}.
 *
 * <p>The deadline starts when the request has been sent, like the response timeout it backs up. For
 * a request without a body that is when the request enters the handler, for a request with a body
 * it is when the last byte of the body has been handed to the connection. A slow upload is
 * therefore never cut off by the deadline.
 *
 * <p>The handler must be registered directly in front of the transport: only there the connection
 * has already been leased and connected, so waiting for a pooled connection does not count, and the
 * retry executor, which sits in front of it, runs it again for every attempt.
 *
 * <p>Discarding closes the whole connection, which is only safe as long as HTTP/2 multiplexing is
 * off, i.e. one exchange per connection at a time.
 */
final class ResponseDeadlineChainHandler implements AsyncExecChainHandler {

  static final String NAME = "response-deadline";

  private static final Logger LOG = LoggerFactory.getLogger(ResponseDeadlineChainHandler.class);

  private static final String TIMER_THREAD_NAME = "camunda-client-response-deadline";
  private static final long TIMER_IDLE_TIMEOUT_SECONDS = 10;

  // Shared by all clients. Its thread ends when no deadline is pending, so there is nothing to shut
  // down when a client is closed.
  private static final ScheduledThreadPoolExecutor TIMER = createTimer();

  private final long marginMillis;

  ResponseDeadlineChainHandler(final Duration margin) {
    marginMillis = margin.toMillis();
  }

  private static ScheduledThreadPoolExecutor createTimer() {
    final ScheduledThreadPoolExecutor timer =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              final Thread thread = new Thread(runnable, TIMER_THREAD_NAME);
              thread.setDaemon(true);
              return thread;
            });
    timer.setKeepAliveTime(TIMER_IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    timer.allowCoreThreadTimeOut(true);
    // nearly every deadline is cancelled long before it is due, and must not stay queued until then
    timer.setRemoveOnCancelPolicy(true);
    return timer;
  }

  @Override
  public void execute(
      final HttpRequest request,
      final AsyncEntityProducer entityProducer,
      final Scope scope,
      final AsyncExecChain chain,
      final AsyncExecCallback callback)
      throws HttpException, IOException {
    final Timeout responseTimeout =
        scope.clientContext.getRequestConfigOrDefault().getResponseTimeout();
    if (responseTimeout == null || responseTimeout.isDisabled()) {
      chain.proceed(request, entityProducer, scope, callback);
      return;
    }

    final Exchange exchange =
        new Exchange(scope, callback, responseTimeout.toMilliseconds() + marginMillis);
    final AsyncEntityProducer producer;
    if (entityProducer == null) {
      exchange.startDeadline();
      producer = null;
    } else {
      producer = new DeadlineStartingProducer(entityProducer, exchange::startDeadline);
    }
    try {
      chain.proceed(request, producer, scope, exchange);
    } catch (final HttpException | IOException | RuntimeException e) {
      exchange.abandon();
      throw e;
    }
  }

  private enum State {
    /** Waiting for the response head; the deadline may still fire. */
    PENDING,
    /** The response head arrived first, the deadline can no longer fire. */
    RESPONDED,
    /** The deadline fired first and the caller was told the exchange failed. */
    EXPIRED,
    /** The exchange ended on its own, successfully or not. */
    DONE
  }

  private static final class Exchange implements AsyncExecCallback {

    private final Scope scope;
    private final AsyncExecCallback delegate;
    private final long deadlineMillis;
    private final AtomicBoolean deadlineStarted = new AtomicBoolean();
    // A single atomic state decides whether the response head or the deadline wins, so the caller
    // never sees both a response and a timeout.
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private final AtomicReference<ScheduledFuture<?>> timer = new AtomicReference<>();

    private Exchange(
        final Scope scope, final AsyncExecCallback delegate, final long deadlineMillis) {
      this.scope = scope;
      this.delegate = delegate;
      this.deadlineMillis = deadlineMillis;
    }

    /** Starts the deadline once, however often and from whichever thread it is called. */
    private void startDeadline() {
      if (!deadlineStarted.compareAndSet(false, true)) {
        return;
      }
      final ScheduledFuture<?> scheduled =
          TIMER.schedule(this::expire, deadlineMillis, TimeUnit.MILLISECONDS);
      timer.set(scheduled);
      // The exchange may have ended before the timer was stored. Every path that ends the exchange
      // first sets the state and only then looks for the timer, so one of the two sees the other.
      if (state.get() != State.PENDING) {
        scheduled.cancel(false);
      }
    }

    private void expire() {
      if (!state.compareAndSet(State.PENDING, State.EXPIRED)) {
        return;
      }
      // The caller may have given up already, e.g. by closing a job worker. The cancellation does
      // not
      // end the exchange for this handler, so the connection can still be leased here. It must be
      // released all the same, but there is nobody left to tell, and nothing to warn about.
      final boolean cancelled = scope.cancellableDependency.isCancelled();
      if (cancelled) {
        LOG.debug(
            "Exchange {} was cancelled and got no response, discarding the connection",
            scope.exchangeId);
      } else {
        LOG.warn(
            "No response received for exchange {} before the client-side deadline, discarding the connection",
            scope.exchangeId);
      }
      try {
        scope.execRuntime.discardEndpoint();
      } catch (final RuntimeException e) {
        LOG.debug("Failed to discard endpoint of exchange {}", scope.exchangeId, e);
      }
      if (cancelled) {
        return;
      }
      try {
        delegate.failed(
            new SocketTimeoutException("No response received before the client-side deadline"));
      } catch (final RuntimeException e) {
        // runs on the timer thread, where nobody would otherwise see the exception
        LOG.warn("Failed to report the expired exchange {}", scope.exchangeId, e);
      }
      // Stop an exchange that is still waiting, e.g. for a connection lease, so that it does not
      // send its request after the caller has been told that it failed.
      try {
        scope.cancellableDependency.cancel();
      } catch (final RuntimeException e) {
        LOG.debug("Failed to cancel expired exchange {}", scope.exchangeId, e);
      }
    }

    private void cancelTimer() {
      final ScheduledFuture<?> scheduled = timer.get();
      if (scheduled != null) {
        scheduled.cancel(false);
      }
    }

    /** Gives up on an exchange whose request could not even be handed to the chain. */
    private void abandon() {
      state.set(State.DONE);
      cancelTimer();
    }

    /**
     * Ends the exchange unless the deadline already did. Returns whether the caller must be told.
     */
    private boolean finish() {
      final boolean notify =
          state.getAndUpdate(s -> s == State.EXPIRED ? State.EXPIRED : State.DONE) != State.EXPIRED;
      cancelTimer();
      return notify;
    }

    @Override
    public AsyncDataConsumer handleResponse(
        final HttpResponse response, final EntityDetails entityDetails)
        throws HttpException, IOException {
      // The head arrived, so the response is no longer silent. The remaining body is bounded by
      // the regular socket timeouts.
      if (!state.compareAndSet(State.PENDING, State.RESPONDED) && state.get() == State.EXPIRED) {
        throw new IOException("Exchange already failed by the client-side deadline");
      }
      cancelTimer();
      return delegate.handleResponse(response, entityDetails);
    }

    @Override
    public void handleInformationResponse(final HttpResponse response)
        throws HttpException, IOException {
      delegate.handleInformationResponse(response);
    }

    @Override
    public void completed() {
      if (finish()) {
        delegate.completed();
      }
    }

    @Override
    public void failed(final Exception cause) {
      if (finish()) {
        delegate.failed(cause);
      }
    }
  }

  /** Reports when the whole body has been handed to the connection. */
  private static final class DeadlineStartingProducer implements AsyncEntityProducer {

    private final AsyncEntityProducer delegate;
    private final Runnable onBodySent;

    private DeadlineStartingProducer(
        final AsyncEntityProducer delegate, final Runnable onBodySent) {
      this.delegate = delegate;
      this.onBodySent = onBodySent;
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
      delegate.produce(
          new DataStreamChannel() {
            @Override
            public void requestOutput() {
              channel.requestOutput();
            }

            @Override
            public int write(final ByteBuffer src) throws IOException {
              return channel.write(src);
            }

            @Override
            public void endStream() throws IOException {
              channel.endStream();
              onBodySent.run();
            }

            @Override
            public void endStream(final List<? extends Header> trailers) throws IOException {
              channel.endStream(trailers);
              onBodySent.run();
            }
          });
    }

    @Override
    public int available() {
      return delegate.available();
    }

    @Override
    public boolean isRepeatable() {
      return delegate.isRepeatable();
    }

    @Override
    public void failed(final Exception cause) {
      delegate.failed(cause);
    }

    @Override
    public void releaseResources() {
      delegate.releaseResources();
    }

    @Override
    public long getContentLength() {
      return delegate.getContentLength();
    }

    @Override
    public String getContentType() {
      return delegate.getContentType();
    }

    @Override
    public String getContentEncoding() {
      return delegate.getContentEncoding();
    }

    @Override
    public boolean isChunked() {
      return delegate.isChunked();
    }

    @Override
    public Set<String> getTrailerNames() {
      return delegate.getTrailerNames();
    }
  }
}
