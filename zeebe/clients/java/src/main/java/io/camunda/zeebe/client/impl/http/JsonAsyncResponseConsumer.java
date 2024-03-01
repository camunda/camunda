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
import io.camunda.zeebe.client.impl.http.JsonAsyncResponseConsumer.JsonResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Consumes either a successful JSON response body of type {@link T} or a {@link
 * io.camunda.zeebe.gateway.protocol.rest.ProblemDetail} returned by the server on error (if the
 * error was generated explicitly by the server, e.g. not a connection error)
 *
 * <p>If there is no body at all, the result will be null (regardless of whether the call was
 * successful or not).
 *
 * @param <T> the type of the successful response body
 */
final class JsonAsyncResponseConsumer<T>
    extends AbstractAsyncResponseConsumer<JsonResponse<T>, JsonEntity<T>> {

  JsonAsyncResponseConsumer(
      final ObjectMapper jsonMapper, final Class<T> type, final int maxCapacity) {
    super(new JsonAsyncEntityConsumer<>(jsonMapper, type, maxCapacity));
  }

  @Override
  protected JsonResponse<T> buildResult(
      final HttpResponse response, final JsonEntity<T> entity, final ContentType contentType) {
    return new JsonResponse<>(response.getCode(), response.getReasonPhrase(), entity);
  }

  @Override
  public void informationResponse(final HttpResponse response, final HttpContext context) {}

  static final class JsonResponse<T> extends BasicHttpResponse {

    private final JsonEntity<T> entity;

    JsonResponse(final int code, final String reasonPhrase, final JsonEntity<T> entity) {
      super(code, reasonPhrase);
      this.entity = entity;
    }

    public JsonEntity<T> entity() {
      return entity;
    }
  }
}
