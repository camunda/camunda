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
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChain.Scope;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets the response consumer abort its exchange while the response body is still arriving.
 *
 * <p>httpclient5 offers no way to do this: the future of the exchange completes once the response
 * head is handed out, and cancelling it after that does nothing. A document stream that the
 * application closes before the end must still end the download, also when the server sends nothing
 * more, so this handler keeps the exchange's runtime in the request context, where {@link
 * #abort(HttpContext)} finds it.
 *
 * <p>The handler also records that the exchange was aborted, so that {@link HttpRetryStrategy} does
 * not send the request again when the abort makes the download fail. The context keeps its
 * attributes in a plain map, which the thread that aborts must not change while the I/O thread may
 * use it. So the handler puts an object into the context before the request goes out, and aborting
 * only changes that object.
 *
 * <p>Aborting discards the connection: it is closed right away and its lease is released. This
 * closes the whole connection, which is only safe as long as HTTP/2 multiplexing is off, i.e. one
 * exchange per connection at a time; {@code HttpClientFactory} turns it off.
 */
final class ExchangeAbortChainHandler implements AsyncExecChainHandler {

  static final String NAME = "exchange-abort";

  private static final String EXCHANGE_ATTRIBUTE =
      ExchangeAbortChainHandler.class.getName() + ".exchange";

  private static final Logger LOG = LoggerFactory.getLogger(ExchangeAbortChainHandler.class);

  @Override
  public void execute(
      final HttpRequest request,
      final AsyncEntityProducer entityProducer,
      final Scope scope,
      final AsyncExecChain chain,
      final AsyncExecCallback callback)
      throws HttpException, IOException {
    scope.clientContext.setAttribute(EXCHANGE_ATTRIBUTE, new AbortableExchange(scope.execRuntime));
    chain.proceed(request, entityProducer, scope, callback);
  }

  /**
   * Aborts the exchange that runs with this context. Does nothing if the exchange has ended, as its
   * connection has been released then, or if the request did not go through this handler.
   */
  static void abort(final HttpContext context) {
    final AbortableExchange exchange = exchange(context);
    if (exchange == null) {
      return;
    }
    // set before the connection is closed, so the I/O thread sees it once it fails on the closed
    // connection and asks the retry strategy whether to send the request again
    exchange.aborted = true;
    try {
      exchange.execRuntime.discardEndpoint();
    } catch (final RuntimeException e) {
      LOG.debug("Failed to discard the connection of an aborted exchange", e);
    }
  }

  /** Returns whether {@link #abort(HttpContext)} was called for the exchange of this context. */
  static boolean isAborted(final HttpContext context) {
    final AbortableExchange exchange = exchange(context);
    return exchange != null && exchange.aborted;
  }

  private static AbortableExchange exchange(final HttpContext context) {
    final Object exchange = context.getAttribute(EXCHANGE_ATTRIBUTE);
    return exchange instanceof AbortableExchange ? (AbortableExchange) exchange : null;
  }

  private static final class AbortableExchange {
    private final AsyncExecRuntime execRuntime;
    private volatile boolean aborted;

    private AbortableExchange(final AsyncExecRuntime execRuntime) {
      this.execRuntime = execRuntime;
    }
  }
}
