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

import io.camunda.zeebe.client.impl.http.ApiResponseConsumer.ApiResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Consumes either a successful JSON response body of type {@link T} or a {@link
 * io.camunda.client.protocol.rest.ProblemDetail} returned by the server on error (if the error was
 * generated explicitly by the server, e.g. not a connection error)
 *
 * <p>If there is no body at all, the result will be null (regardless of whether the call was
 * successful or not).
 *
 * @param <T> the type of the successful response body
 */
final class ApiResponseConsumer<T>
    extends AbstractAsyncResponseConsumer<ApiResponse<T>, ApiEntity<T>> {

  ApiResponseConsumer(final AsyncEntityConsumer<ApiEntity<T>> entityConsumer) {
    super(entityConsumer);
  }

  @Override
  protected ApiResponse<T> buildResult(
      final HttpResponse response, final ApiEntity<T> entity, final ContentType contentType) {
    return new ApiResponse<>(response.getCode(), entity);
  }

  @Override
  public void informationResponse(final HttpResponse response, final HttpContext context) {}

  static final class ApiResponse<T> extends BasicHttpResponse {

    private final ApiEntity<T> entity;

    ApiResponse(final int code, final ApiEntity<T> entity) {
      super(code);
      this.entity = entity;
    }

    public ApiEntity<T> entity() {
      return entity;
    }
  }
}
