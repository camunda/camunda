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
import io.camunda.zeebe.client.impl.http.CircularBufferInputStream.CapacityCallback;
import io.camunda.zeebe.client.impl.http.TypedApiEntityConsumer.JsonApiEntityConsumer;
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
 * <p>This implementation guarantees that the data is consumed in a non-blocking manner, and that
 * the data is stored in a buffer with a fixed capacity. The consumer works with a {@link
 * CapacityChannel} to notify the I/O layer when more data can be read from the network.
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
      inputStream.setCapacityCallback(this);
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

    this.capacityChannel = capacityChannel;
    final int availableSpace = inputStream.getAvailableSpace();
    if (availableSpace > 0) {
      capacityChannel.update(availableSpace);
    }
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

    inputStream.endOfStream();
    completed = true;
  }

  @Override
  public void releaseResources() {
    // nothing to release
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
