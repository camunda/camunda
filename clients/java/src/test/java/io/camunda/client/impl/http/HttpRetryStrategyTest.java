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

import java.io.IOException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
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
