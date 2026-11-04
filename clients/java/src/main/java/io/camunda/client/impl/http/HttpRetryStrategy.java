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
 */
final class HttpRetryStrategy extends DefaultHttpRequestRetryStrategy {

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
