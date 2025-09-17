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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.async.NonBlockingByteBufferJsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.camunda.client.protocol.rest.ProblemDetail;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic interface for consuming API entities from an asynchronous data stream. This interface
 * defines the basic operations required by {@link ApiEntityConsumer} to handle different types of
 * API responses, including JSON and raw byte data.
 *
 * @param <T> the type of the entity expected in the successful response
 */
public interface TypedApiEntityConsumer<T> {

  /**
   * Generates the final content after all data has been consumed.
   *
   * @return an {@link ApiEntity} containing the deserialized content, or null if no data was
   *     consumed
   */
  ApiEntity<T> generateContent() throws IOException;

  /**
   * Consumes data from the provided {@link ByteBuffer}. This method is called as data becomes
   * available and can be invoked multiple times as more data is streamed.
   *
   * @param src the {@link ByteBuffer} containing the data to be consumed
   * @param endOfStream a flag indicating whether this is the last chunk of data
   */
  void consumeData(final ByteBuffer src, final boolean endOfStream) throws IOException;

  /**
   * Releases any resources associated with this consumer. This method should be called once the
   * consumer is no longer needed, such as after the response has been fully processed.
   */
  void releaseResources();

  /**
   * Returns the number of bytes currently buffered by this consumer.
   *
   * @return the number of buffered bytes
   */
  int getBufferedBytes();

  /**
   * A {@link TypedApiEntityConsumer} implementation for handling JSON data. This consumer uses
   * Jackson's non-blocking parser to incrementally parse JSON data as it is streamed.
   */
  class JsonApiEntityConsumer<T> implements TypedApiEntityConsumer<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonApiEntityConsumer.class);
    private final ObjectMapper json;
    private final Class<T> type;
    private final NonBlockingByteBufferJsonParser parser;
    private final TokenBuffer buffer;
    private final boolean isResponse;
    private int bufferedBytes;

    public JsonApiEntityConsumer(
        final ObjectMapper json, final Class<T> type, final boolean isResponse) throws IOException {
      this.json = json;
      this.type = type;
      this.isResponse = isResponse;
      parser =
          (NonBlockingByteBufferJsonParser) json.getFactory().createNonBlockingByteBufferParser();
      buffer = new TokenBuffer(parser, json.getDeserializationContext());
    }

    @Override
    public ApiEntity<T> generateContent() throws IOException {
      try {
        if (isResponse) {
          return ApiEntity.of(json.readValue(buffer.asParserOnFirstToken(), type));
        }
        return ApiEntity.of(json.readValue(buffer.asParserOnFirstToken(), ProblemDetail.class));
      } catch (final IOException ioe) {
        LOGGER.warn("Could not read JSON content", ioe);
        // write the original JSON response into an error response
        final String jsonString = getJsonString();
        return ApiEntity.of(
            new ProblemDetail()
                .title("Cannot parse the server JSON response")
                .status(500)
                .detail(
                    "The client failed to parse the JSON response: "
                        + ioe.getMessage()
                        + ". The received JSON payload is: "
                        + jsonString));
      }
    }

    @Override
    public void consumeData(final ByteBuffer src, final boolean endOfStream) throws IOException {
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
      try {
        parser.close();
      } catch (final Exception e) {
        // log but otherwise ignore
        LOGGER.warn("Failed to close JSON parser", e);
      }
      try {
        buffer.close();
      } catch (final IOException e) {
        // log but otherwise ignore
        LOGGER.warn("Failed to close JSON buffer", e);
      }
      bufferedBytes = 0;
    }

    @Override
    public int getBufferedBytes() {
      return bufferedBytes;
    }

    private String getJsonString() {
      try (final ByteArrayOutputStream output = new ByteArrayOutputStream();
          final JsonGenerator generator = json.createGenerator(output)) {
        buffer.serialize(generator);
        generator.flush();
        return output.toString(StandardCharsets.UTF_8.name());
      } catch (final Exception ex) {
        LOGGER.warn("Failed to serialize JSON string", ex);
        return "Original response cannot be constructed";
      }
    }
  }

  /**
   * A {@link TypedApiEntityConsumer} implementation for handling raw data, which could be text or
   * binary. This consumer accumulates the raw data into a byte array, which can later be converted
   * to a string or another appropriate type.
   */
  class RawApiEntityConsumer<T> implements TypedApiEntityConsumer<T> {

    private final boolean isResponse;
    private final int maxCapacity;

    private byte[] body = new byte[1024];

    private int bufferedBytes;

    public RawApiEntityConsumer(final boolean isResponse, final int maxCapacity) {
      this.isResponse = isResponse;
      this.maxCapacity = maxCapacity;
    }

    @Override
    public ApiEntity<T> generateContent() {
      if (bufferedBytes == 0) {
        return null;
      }
      if (isResponse) {
        return (ApiEntity<T>)
            ApiEntity.of(new String(body, 0, bufferedBytes, StandardCharsets.UTF_8));
      }

      return ApiEntity.of(ByteBuffer.wrap(body, 0, bufferedBytes));
    }

    @Override
    public void consumeData(final ByteBuffer src, final boolean endOfStream) {
      final int offset = bufferedBytes;
      bufferedBytes += src.remaining();
      if (body.length < bufferedBytes) {
        if (bufferedBytes > maxCapacity) {
          throw new IllegalArgumentException(
              "The message size exceeds the maximum allowed size of " + maxCapacity);
        }
        body = Arrays.copyOf(body, bufferedBytes);
      }
      src.get(body, offset, src.remaining());
    }

    @Override
    public void releaseResources() {
      bufferedBytes = 0;
    }

    @Override
    public int getBufferedBytes() {
      return bufferedBytes;
    }
  }

  class TextStreamJsonApiEntityConsumer<T> implements TypedApiEntityConsumer<T> {
    private static final Logger LOG =
        LoggerFactory.getLogger(TextStreamJsonApiEntityConsumer.class);
    private final ObjectMapper mapper;
    private final Class<T> type;
    private final boolean isResponse;
    private final ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(1024);
    private int bufferedBytes;
    private T lastItem; // last parsed payload (for responses)
    private ProblemDetail lastProblem; // last parsed problem (for errors)
    private final StringBuilder sseData =
        new StringBuilder(); // accumulates current SSE event's data

    TextStreamJsonApiEntityConsumer(
        final ObjectMapper mapper, final Class<T> type, final boolean isResponse) {
      this.mapper = mapper;
      this.type = type;
      this.isResponse = isResponse;
    }

    private void processLine(final String line) {
      if (line.isEmpty()) {
        // blank line => end of event
        finishSseEvent();
        return;
      }
      if (line.startsWith(":")) {
        return; // sse comment, ignore
      }
      if (line.startsWith("data:")) {
        // per spec: everything after the first colon, strip one leading space if present
        String v = line.substring(5);
        if (!v.isEmpty() && v.charAt(0) == ' ') {
          v = v.substring(1);
        }
        if (sseData.length() > 0) {
          sseData.append('\n');
        }
        sseData.append(v);
      }
      // we ignore 'event:'/'id:' for this one-shot use-case
    }

    private void finishSseEvent() {
      if (sseData.length() == 0) {
        return;
      }
      parseJson(sseData.toString());
      sseData.setLength(0);
    }

    private void parseJson(final String json) {
      try {
        if (isResponse) {
          lastItem = mapper.readValue(json, type);
        } else {
          lastProblem = mapper.readValue(json, ProblemDetail.class);
        }
      } catch (final IOException e) {
        LOG.warn("Failed to parse streaming JSON chunk: {}", json, e);
        // Keep best-effort behavior: wrap as ProblemDetail if response mode
        if (isResponse) {
          lastProblem =
              new ProblemDetail()
                  .title("Cannot parse streaming JSON payload")
                  .status(500)
                  .detail(e.getMessage());
        }
      }
    }

    @Override
    public ApiEntity<T> generateContent() {
      if (isResponse) {
        if (lastItem != null) {
          return ApiEntity.of(lastItem);
        }
        if (lastProblem != null) {
          return ApiEntity.of(lastProblem);
        }
        return null;
      } else {
        if (lastProblem != null) {
          return ApiEntity.of(lastProblem);
        }
        // if server streamed a successful payload to an error-typed consumer, treat as problem
        return ApiEntity.of(
            new ProblemDetail().title("Empty streaming response").status(204).detail("No content"));
      }
    }

    @Override
    public void consumeData(final ByteBuffer src, final boolean endOfStream) throws IOException {
      final int rem = src.remaining();
      bufferedBytes += rem;

      // Feed bytes line-by-line
      while (src.hasRemaining()) {
        final byte b = src.get();
        if (b == '\n') {
          processLine(lineBuf.toString(StandardCharsets.UTF_8.name()));
          lineBuf.reset();
        } else if (b == '\r') {
          // ignore, handle \r\n
        } else {
          lineBuf.write(b);
        }
      }

      if (endOfStream) {
        // flush last partial line
        if (lineBuf.size() > 0) {
          processLine(lineBuf.toString(StandardCharsets.UTF_8.name()));
          lineBuf.reset();
        }
        // finalize any pending SSE event
        finishSseEvent();
      }
    }

    @Override
    public void releaseResources() {
      bufferedBytes = 0;
      sseData.setLength(0);
    }

    @Override
    public int getBufferedBytes() {
      return bufferedBytes;
    }
  }
}
