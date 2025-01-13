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
package io.camunda.zeebe.client.impl.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.zeebe.client.impl.http.TypedApiEntityConsumer.JsonApiEntityConsumer;
import io.camunda.zeebe.client.impl.http.TypedApiEntityConsumer.RawApiEntityConsumer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;

/**
 * Asynchronously consumes the response body as a JSON entity of type {@link T}, or if the server
 * returns an error, of type {@link ProblemDetail}. This is detected via the content type header. If
 * the server returns:
 *
 * <ul>
 *   <li>application/json: the body type is expected to be {@link T}
 *   <li>application/problem+json: the body type is expected to be {@link ProblemDetail}
 *   <li>text/xml: the body is handled as a raw string.
 * </ul>
 *
 * Anything else will cause an error to be propagated to the response consumer.
 *
 * <p>NOTE: this consumer only builds up the abstract JSON tree asynchronously as data comes in,
 * parsing each token one by one as it feeds more input into the underlying buffer. The actual
 * conversion to a Java type is only done at the very end, as it's not easy to asynchronously build
 * up the POJOs. The resources (incl. the underlying buffer) are released when the request context
 * is closed.
 *
 * @param <T> the type of the successful response body
 */
final class ApiEntityConsumer<T> extends AbstractBinAsyncEntityConsumer<ApiEntity<T>> {
  private static final List<ContentType> SUPPORTED_TEXT_CONTENT_TYPES =
      Arrays.asList(ContentType.TEXT_XML);
  private final ObjectMapper json;
  private final Class<T> type;
  private final int chunkSize;

  private TypedApiEntityConsumer<T> entityConsumer;

  ApiEntityConsumer(final ObjectMapper json, final Class<T> type, final int chunkSize) {
    this.json = json;
    this.type = type;
    this.chunkSize = chunkSize;
  }

  @Override
  protected void streamStart(final ContentType contentType) throws IOException {
    if (ContentType.APPLICATION_JSON.isSameMimeType(contentType)) {
      entityConsumer = new JsonApiEntityConsumer<>(json, type, true);
    } else if (ContentType.APPLICATION_PROBLEM_JSON.isSameMimeType(contentType)) {
      entityConsumer = new JsonApiEntityConsumer<>(json, type, false);
    } else {
      final boolean isResponse =
          String.class.equals(type)
              && SUPPORTED_TEXT_CONTENT_TYPES.stream().anyMatch(t -> t.isSameMimeType(contentType));
      entityConsumer = new RawApiEntityConsumer<>(isResponse, chunkSize);
    }
  }

  @Override
  protected ApiEntity<T> generateContent() throws IOException {
    return entityConsumer.generateContent();
  }

  @Override
  protected int capacityIncrement() {
    return chunkSize;
  }

  @Override
  protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException {
    entityConsumer.consumeData(src, endOfStream);
  }

  @Override
  public void releaseResources() {
    if (entityConsumer != null) {
      entityConsumer.releaseResources();
    }
  }
}
