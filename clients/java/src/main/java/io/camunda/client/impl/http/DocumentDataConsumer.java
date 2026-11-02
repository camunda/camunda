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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.impl.http.CircularBufferInputStream.CapacityCallback;
import io.camunda.client.impl.http.TypedApiEntityConsumer.JsonApiEntityConsumer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * An asynchronous consumer for binary data that represents a document.
 *
 * <p>This implementation guarantees that the data is consumed in a non-blocking manner, and that it
 * asks the I/O layer for no more data than fits into a buffer with a fixed capacity. The consumer
 * works with a {@link CapacityChannel} to notify the I/O layer when more data can be read from the
 * network. The I/O layer may still hand over more than that; the buffer then grows to hold it.
 *
 * <p>If the server returns:
 *
 * <ul>
 *   <li>application/octet-stream: the data is returned as an {@link InputStream}
 *   <li>application/problem+json: the data is returned as a {@link
 *       io.camunda.client.protocol.rest.ProblemDetail}
 * </ul>
 *
 * Anything else will cause an error to be propagated to the caller.
 *
 * <p>The input stream response is returned via the result callback immediately as soon as the
 * content type is determined to be {@code application/octet-stream}. This allows the caller to
 * start reading the data in a streaming fashion and does not require the entire response body to be
 * buffered in memory.
 *
 * @param <T> the type of the successful response body, always an {@link InputStream}
 */
public class DocumentDataConsumer<T>
    implements AsyncEntityConsumer<ApiEntity<T>>, CapacityCallback {

  private final CircularBufferInputStream inputStream;
  private FutureCallback<ApiEntity<T>> resultCallback;
  private final int maxCapacity;
  private volatile CapacityChannel capacityChannel;
  private volatile boolean completed = false;
  private volatile boolean problemDetail = false;

  private final JsonApiEntityConsumer<InputStream> problemDetailConsumer;

  public DocumentDataConsumer(final int bufferCapacity, final ObjectMapper json) {
    maxCapacity = bufferCapacity;
    inputStream = new CircularBufferInputStream(bufferCapacity);
    try {
      problemDetailConsumer = new JsonApiEntityConsumer<>(json, InputStream.class, false);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void streamStart(
      final EntityDetails entityDetails, final FutureCallback<ApiEntity<T>> resultCallback) {
    this.resultCallback = resultCallback;
    final ContentType contentType =
        entityDetails != null ? ContentType.parse(entityDetails.getContentType()) : null;
    if (ContentType.APPLICATION_PROBLEM_JSON.isSameMimeType(contentType)) {
      problemDetail = true;
    } else {
      problemDetail = false;
      resultCallback.completed((ApiEntity<T>) ApiEntity.of(inputStream));
    }
  }

  @Override
  public void failed(final Exception cause) {
    if (cause instanceof IOException) {
      inputStream.signalError((IOException) cause);
    } else {
      inputStream.signalError(new IOException(cause));
    }
    if (resultCallback != null && !completed) {
      resultCallback.failed(cause);
    }
  }

  @Override
  public ApiEntity<T> getContent() {
    if (problemDetail) {
      try {
        return (ApiEntity<T>) problemDetailConsumer.generateContent();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
    return (ApiEntity<T>) ApiEntity.of(inputStream);
  }

  @Override
  public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
    if (problemDetail) {
      capacityChannel.update(maxCapacity - problemDetailConsumer.getBufferedBytes());
      return;
    }

    // The I/O layer calls this whenever its window runs low, and HTTP/1.1 stops reading until it is
    // granted more. Its window can be smaller than the free space, as it also counts data it pushed
    // beyond the window, so the free space is granted again on every call. The stream restarts
    // counting freed space from here, which keeps it from granting that space a second time.
    this.capacityChannel = capacityChannel;
    inputStream.reportAvailableSpace(this);
  }

  @Override
  public void consume(final ByteBuffer src) throws IOException {
    if (problemDetail) {
      problemDetailConsumer.consumeData(src, false);
    }
    inputStream.write(src);
  }

  @Override
  public void streamEnd(final List<? extends Header> trailers) throws IOException {
    if (problemDetail) {
      problemDetailConsumer.consumeData(ByteBuffer.allocate(0), true);
      resultCallback.completed((ApiEntity<T>) problemDetailConsumer.generateContent());
      return;
    }

    completed = true;
    // errors signalled after the end of the stream are ignored by the stream itself, so a late
    // releaseResources() cannot flag a fully received document as aborted
    inputStream.endOfStream();
  }

  @Override
  public void releaseResources() {
    // AbstractAsyncResponseConsumer#failed only releases resources and never reaches our own
    // #failed, so a cancelled or aborted exchange would otherwise leave a blocked reader waiting
    // for data that never arrives. This is a no-op once the stream has ended.
    if (!problemDetail) {
      inputStream.signalError(
          new IOException(
              "Document stream was aborted before the response was fully received (connection"
                  + " closed, timed out or cancelled)"));
    }
  }

  @Override
  public void onCapacityAvailable(final int increment) {
    if (capacityChannel != null && increment > 0) {
      try {
        capacityChannel.update(increment);
      } catch (final IOException e) {
        failed(e);
      }
    }
  }
}
