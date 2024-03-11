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

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.async.NonBlockingByteBufferJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.camunda.zeebe.client.protocol.rest.ProblemDetail;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
final class JsonAsyncEntityConsumer<T> extends AbstractBinAsyncEntityConsumer<JsonEntity<T>> {
  private final ObjectMapper json;
  private final Class<T> type;
  private final int maxCapacity;

  private NonBlockingByteBufferJsonParser parser;
  private TokenBuffer buffer;
  private int bufferedBytes;
  private boolean isResponse;

  JsonAsyncEntityConsumer(final ObjectMapper json, final Class<T> type, final int maxCapacity) {
    this.json = json;
    this.type = type;
    this.maxCapacity = maxCapacity;
  }

  @Override
  protected void streamStart(final ContentType contentType) throws IOException {
    if (contentType == null) {
      throw new IOException("Expected to parse a JSON response, but no content type detected");
    }

    if (ContentType.APPLICATION_JSON.isSameMimeType(contentType)) {
      isResponse = true;
    } else if (ContentType.APPLICATION_PROBLEM_JSON.isSameMimeType(contentType)) {
      isResponse = false;
    } else {
      throw new IOException(
          "Expected to parse a application/json response or an application/problem+json error, but got: "
              + contentType);
    }

    // Jackson does not support asynchronous parsing of any charset but UTF-8
    // If this is required, we can always perform some transcoding, or we switch to synchronous
    // parsing
    if (contentType.getCharset() != null
        && !StandardCharsets.UTF_8.equals(contentType.getCharset())) {
      throw new IOException("Expected to parse UTF-8 JSON, but got: " + contentType.getCharset());
    }

    parser =
        (NonBlockingByteBufferJsonParser) json.getFactory().createNonBlockingByteBufferParser();
    buffer = new TokenBuffer(parser, json.getDeserializationContext());
  }

  @Override
  protected JsonEntity<T> generateContent() throws IOException {
    if (parser == null || buffer == null) {
      return null;
    }

    buffer.asParserOnFirstToken();

    if (isResponse) {
      return JsonEntity.of(json.readValue(buffer.asParserOnFirstToken(), type));
    }

    return JsonEntity.of(json.readValue(buffer.asParserOnFirstToken(), ProblemDetail.class));
  }

  @Override
  protected int capacityIncrement() {
    return maxCapacity - bufferedBytes;
  }

  @Override
  protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException {
    bufferedBytes += src.remaining();
    parser.feedInput(src);
    JsonToken jsonToken = parser.nextToken();
    while (jsonToken != null && jsonToken != JsonToken.NOT_AVAILABLE) {
      buffer.copyCurrentEvent(parser);
      jsonToken = parser.nextToken();
    }

    if (endOfStream) {
      parser.endOfInput();
    }
  }

  @Override
  public void releaseResources() {
    if (parser != null) {
      try {
        parser.close();
      } catch (final Exception e) {
        // log but otherwise ignore
      }
    }

    if (buffer != null) {
      try {
        buffer.close();
      } catch (final IOException e) {
        // log but otherwise ignore
      }
    }

    bufferedBytes = 0;
  }
}
