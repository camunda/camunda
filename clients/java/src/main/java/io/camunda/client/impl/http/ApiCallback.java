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
import io.camunda.client.api.ProblemDetail;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.MalformedResponseException;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.impl.HttpStatusCode;
import io.camunda.client.impl.Loggers;
import io.camunda.client.impl.http.ApiResponseConsumer.ApiResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.apache.hc.core5.concurrent.FutureCallback;

final class ApiCallback<HttpT, RespT> implements FutureCallback<ApiResponse<HttpT>> {
  private static final Predicate<Integer> DEFAULT_SUCCESS_PREDICATE =
      code -> code >= 200 && code < 400;
  private final CompletableFuture<RespT> response;
  private final JsonResponseAndStatusCodeTransformer<HttpT, RespT> transformer;
  private final Predicate<Integer> successPredicate;
  private final Predicate<StatusCode> retryPredicate;
  private final Runnable retryAction;
  private final AtomicInteger remainingRetries;

  public ApiCallback(
      final CompletableFuture<RespT> response,
      final JsonResponseAndStatusCodeTransformer<HttpT, RespT> transformer,
      final Predicate<Integer> successPredicate,
      final Predicate<StatusCode> retryPredicate,
      final Runnable retryAction,
      final int maxRetries) {
    this.response = response;
    this.transformer = transformer;
    if (successPredicate != null) {
      this.successPredicate = successPredicate;
    } else {
      this.successPredicate = DEFAULT_SUCCESS_PREDICATE;
    }
    this.retryPredicate = retryPredicate;
    this.retryAction = retryAction;
    remainingRetries = new AtomicInteger(maxRetries);
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
    if (remainingRetries.getAndDecrement() > 0 && retryPredicate.test(new HttpStatusCode(code))) {
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

    final ProblemDetail problem = new ProblemDetail();
    if (body.problem() != null) {
      final String instanceValue = body.problem().getInstance();
      URI instanceUri = null;
      if (instanceValue != null) {
        try {
          instanceUri = URI.create(instanceValue);
        } catch (final IllegalArgumentException e) {
          // Server returned an invalid URI; leave instance as null
          // to avoid masking the original HTTP error
          Loggers.LOGGER.warn(
              "Failed to parse ProblemDetail instance as URI: '{}'. Ignoring invalid value.",
              instanceValue,
              e);
        }
      }
      problem
          .setDetail(body.problem().getDetail())
          .setInstance(instanceUri)
          .setStatus(body.problem().getStatus())
          .setTitle(body.problem().getTitle())
          .setType(body.problem().getType());
    }
    response.completeExceptionally(new ProblemException(code, reason, problem));
  }

  private void handleSuccessResponse(
      final ApiEntity<HttpT> body, final int code, final String reason) {
    if (body == null) {
      completeResponse(code, reason, null);
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

    completeResponse(code, reason, body.response());
  }

  private void completeResponse(final int code, final String reason, final HttpT httpResponse) {
    try {
      response.complete(transformer.transform(httpResponse, code));
    } catch (final Exception e) {
      response.completeExceptionally(new MalformedResponseException(code, reason, e));
    }
  }

  private boolean wasSuccessful(final int code) {
    return successPredicate.test(code);
  }
}
