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
 * <p>Aborting discards the connection: it is closed right away and its lease is released. This
 * closes the whole connection, which is only safe as long as HTTP/2 multiplexing is off, i.e. one
 * exchange per connection at a time; {@code HttpClientFactory} turns it off.
 */
final class ExchangeAbortChainHandler implements AsyncExecChainHandler {

  static final String NAME = "exchange-abort";

  private static final String EXEC_RUNTIME_ATTRIBUTE =
      ExchangeAbortChainHandler.class.getName() + ".execRuntime";

  private static final Logger LOG = LoggerFactory.getLogger(ExchangeAbortChainHandler.class);

  @Override
  public void execute(
      final HttpRequest request,
      final AsyncEntityProducer entityProducer,
      final Scope scope,
      final AsyncExecChain chain,
      final AsyncExecCallback callback)
      throws HttpException, IOException {
    scope.clientContext.setAttribute(EXEC_RUNTIME_ATTRIBUTE, scope.execRuntime);
    chain.proceed(request, entityProducer, scope, callback);
  }

  /**
   * Aborts the exchange that runs with this context. Does nothing if the exchange has ended, as its
   * connection has been released then, or if the request did not go through this handler.
   */
  static void abort(final HttpContext context) {
    final Object execRuntime = context.getAttribute(EXEC_RUNTIME_ATTRIBUTE);
    if (!(execRuntime instanceof AsyncExecRuntime)) {
      return;
    }
    try {
      ((AsyncExecRuntime) execRuntime).discardEndpoint();
    } catch (final RuntimeException e) {
      LOG.debug("Failed to discard the connection of an aborted exchange", e);
    }
  }
}
