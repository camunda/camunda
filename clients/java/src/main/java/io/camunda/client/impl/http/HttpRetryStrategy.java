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
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * The default retry strategy of httpclient5, except that it does not send a document request again
 * once the caller closed the document stream.
 *
 * <p>The caller has stopped reading, so there is nothing to download anymore. Without this,
 * httpclient5 logs that it sends the request again and starts to open a new connection for it, only
 * to give up because the caller already got the response.
 *
 * <p>Closing the stream makes the download fail in one of two ways: the next data that arrives
 * cannot be written to the stream, which fails with {@link DocumentStreamClosedException}, or the
 * next read from the closed connection fails with an I/O error that httpclient5 would otherwise
 * retry, such as a {@link java.nio.channels.ClosedChannelException}. The second case is recognized
 * by {@link ExchangeAbortChainHandler#isAborted(HttpContext)}.
 *
 * <p>If the client handles backpressure itself, this strategy does not send a request again because
 * of the status of the response either, which httpclient5 would do once for 429 and 503. The client
 * then retries according to its own policy, which looks at the body of the response and bounds the
 * number of attempts; a retry by httpclient5 below it would multiply the attempts and hide
 * backpressure signals from the concurrency limit. A failure of the connection is still retried by
 * httpclient5 in both cases.
 */
final class HttpRetryStrategy extends DefaultHttpRequestRetryStrategy {

  private final boolean retryOnResponseStatus;

  /**
   * @param retryOnResponseStatus whether to send a request again because of the status of the
   *     response, as httpclient5 does by default
   */
  HttpRetryStrategy(final boolean retryOnResponseStatus) {
    this.retryOnResponseStatus = retryOnResponseStatus;
  }

  HttpRetryStrategy() {
    this(true);
  }

  @Override
  public boolean retryRequest(
      final HttpResponse response, final int execCount, final HttpContext context) {
    return retryOnResponseStatus && super.retryRequest(response, execCount, context);
  }

  @Override
  public boolean retryRequest(
      final HttpRequest request,
      final IOException exception,
      final int execCount,
      final HttpContext context) {
    return !(exception instanceof DocumentStreamClosedException)
        && !ExchangeAbortChainHandler.isAborted(context)
        && super.retryRequest(request, exception, execCount, context);
  }
}
