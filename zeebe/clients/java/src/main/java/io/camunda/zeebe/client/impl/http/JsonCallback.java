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

import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClientHttpException;
import io.camunda.zeebe.client.api.command.MalformedResponseException;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.impl.http.JsonAsyncResponseConsumer.JsonResponse;
import java.util.concurrent.CompletableFuture;
import org.apache.hc.core5.concurrent.FutureCallback;

final class JsonCallback<HttpT, RespT> implements FutureCallback<JsonResponse<HttpT>> {

  private final CompletableFuture<RespT> response;
  private final JsonResponseTransformer<HttpT, RespT> transformer;

  public JsonCallback(
      final CompletableFuture<RespT> response,
      final JsonResponseTransformer<HttpT, RespT> transformer) {
    this.response = response;
    this.transformer = transformer;
  }

  @Override
  public void completed(final JsonResponse<HttpT> result) {
    final JsonEntity<HttpT> body = result.entity();
    final int code = result.getCode();
    final String reason = result.getReasonPhrase();

    if (wasSuccessful(code)) {
      handleSuccessResponse(body, code, reason);
      return;
    }

    handleErrorResponse(body, code, reason);
  }

  @Override
  public void failed(final Exception ex) {
    if (ex instanceof ClientException) {
      response.completeExceptionally(ex);
      return;
    }

    response.completeExceptionally(new ClientException(ex));
  }

  @Override
  public void cancelled() {
    response.cancel(true);
  }

  private void handleErrorResponse(
      final JsonEntity<HttpT> body, final int code, final String reason) {
    if (body == null) {
      response.completeExceptionally(new ClientHttpException(code, reason));
      return;
    }

    if (!body.isProblem()) {
      response.completeExceptionally(
          new MalformedResponseException(
              "Expected to receive a ProblemDetail as the error body, but got a successful response",
              code,
              reason));
      return;
    }

    response.completeExceptionally(new ProblemException(code, reason, body.problem()));
  }

  private void handleSuccessResponse(
      final JsonEntity<HttpT> body, final int code, final String reason) {
    if (body == null) {
      response.complete(null);
      return;
    }

    if (!body.isResponse()) {
      response.completeExceptionally(
          new MalformedResponseException(
              String.format(
                  "Expected to receive a response body, but got an error: %s", body.problem()),
              code,
              reason));
      return;
    }

    try {
      response.complete(transformer.transform(body.response()));
    } catch (final Exception e) {
      response.completeExceptionally(new MalformedResponseException(code, reason, e));
    }
  }

  private boolean wasSuccessful(final int code) {
    return code >= 200 && code < 400;
  }
}
