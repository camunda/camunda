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

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.impl.http.TypedApiEntityConsumer.RawApiEntityConsumer;
import io.camunda.client.protocol.rest.ProblemDetail;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;

/**
 * An entity consumer that consumes the response body and produces an {@link ApiEntity} with a
 * {@code null} body. This is useful for endpoints that do not return any content in the response
 * body, but we still want to consume the body to ensure the connection can be reused.
 *
 * <p>In case of an error response (4xx or 5xx), the entity consumer will attempt to parse the
 * response body as a {@link ProblemDetail} and include it in the thrown {@link ProblemException}.
 */
final class VoidEntityConsumer extends AbstractBinAsyncEntityConsumer<ApiEntity<Void>> {
  private final int chunkSize;

  private RawApiEntityConsumer<Void> entityConsumer;

  VoidEntityConsumer(final int chunkSize) {
    this.chunkSize = chunkSize;
  }

  @Override
  protected void streamStart(final ContentType contentType) throws IOException {
    final boolean isResponse = !ContentType.APPLICATION_PROBLEM_JSON.isSameMimeType(contentType);
    entityConsumer = new RawApiEntityConsumer<>(isResponse, chunkSize);
  }

  @Override
  protected ApiEntity<Void> generateContent() throws IOException {
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
