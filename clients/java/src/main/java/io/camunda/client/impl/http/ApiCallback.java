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

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.MalformedResponseException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.impl.HttpStatusCode;
import io.camunda.client.impl.ResponseMapper;
import io.camunda.client.impl.http.ApiResponseConsumer.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.apache.hc.core5.concurrent.FutureCallback;

final class ApiCallback<HttpT, RespT> implements FutureCallback<ApiResponse<HttpT>> {

  private final CompletableFuture<RespT> response;
  private final JsonResponseTransformer<HttpT, RespT> transformer;
  private final Predicate<StatusCode> retryPredicate;
  private final Runnable retryAction;
  private final AtomicInteger retries = new AtomicInteger(2);

  public ApiCallback(
      final CompletableFuture<RespT> response,
      final JsonResponseTransformer<HttpT, RespT> transformer,
      final Predicate<StatusCode> retryPredicate,
      final Runnable retryAction) {
    this.response = response;
    this.transformer = transformer;
    this.retryPredicate = retryPredicate;
    this.retryAction = retryAction;
  }

  @Override
  public void completed(final ApiResponse<HttpT> result) {
    final ApiEntity<HttpT> body = result.entity();
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
      final ApiEntity<HttpT> body, final int code, final String reason) {
    if (retries.getAndDecrement() > 0 && retryPredicate.test(new HttpStatusCode(code))) {
      retryAction.run();
      return;
    }

    if (body == null) {
      response.completeExceptionally(new ClientHttpException(code, reason));
      return;
    }

    if (body.isUnknown()) {
      response.completeExceptionally(
          new MalformedResponseException(
              String.format(
                  "Expected to receive a problem body, but got an unknown body type: %s",
                  StandardCharsets.UTF_8.decode(body.unknown())),
              code,
              reason));
      return;
    }

    if (body.isResponse()) {
      response.completeExceptionally(
          new MalformedResponseException(
              String.format(
                  "Expected to receive a problem body, but got an actual response: %s",
                  body.response()),
              code,
              reason));
      return;
    }

    response.completeExceptionally(
        new ProblemException(code, reason, ResponseMapper.fromProtocolObject(body.problem())));
  }

  private void handleSuccessResponse(
      final ApiEntity<HttpT> body, final int code, final String reason) {
    if (body == null) {
      response.complete(null);
      return;
    }

    if (body.isUnknown()) {
      response.completeExceptionally(
          new MalformedResponseException(
              String.format(
                  "Expected to receive a response body, but got an unknown body type: %s",
                  StandardCharsets.UTF_8.decode(body.unknown())),
              code,
              reason));
      return;
    }

    if (body.isProblem()) {
      response.completeExceptionally(
          new MalformedResponseException(
              String.format(
                  "Expected to receive a response body, but got a problem: %s", body.problem()),
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
