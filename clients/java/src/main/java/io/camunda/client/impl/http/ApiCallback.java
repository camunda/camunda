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
import io.camunda.client.impl.backpressure.BackpressureCall;
import io.camunda.client.impl.backpressure.BackpressureSignals;
import io.camunda.client.impl.backpressure.BackpressureSignals.Failure;
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
  private static final String REDACTED_BODY = "<redacted: may contain secret values>";
  private final CompletableFuture<RespT> response;
  private final JsonResponseAndStatusCodeTransformer<HttpT, RespT> transformer;
  private final Predicate<Integer> successPredicate;
  private final Predicate<StatusCode> retryPredicate;
  private final Runnable retryAction;
  private final AtomicInteger remainingRetries;
  private final boolean sensitiveResponseBody;
  private final BackpressureCall backpressure;

  public ApiCallback(
      final CompletableFuture<RespT> response,
      final JsonResponseAndStatusCodeTransformer<HttpT, RespT> transformer,
      final Predicate<Integer> successPredicate,
      final Predicate<StatusCode> retryPredicate,
      final Runnable retryAction,
      final int maxRetries,
      final boolean sensitiveResponseBody) {
    this(
        response,
        transformer,
        successPredicate,
        retryPredicate,
        retryAction,
        maxRetries,
        sensitiveResponseBody,
        null);
  }

  /**
   * @param backpressure the backpressure handling of the request, {@code null} if the request is
   *     not subject to it
   */
  public ApiCallback(
      final CompletableFuture<RespT> response,
      final JsonResponseAndStatusCodeTransformer<HttpT, RespT> transformer,
      final Predicate<Integer> successPredicate,
      final Predicate<StatusCode> retryPredicate,
      final Runnable retryAction,
      final int maxRetries,
      final boolean sensitiveResponseBody,
      final BackpressureCall backpressure) {
    this.backpressure = backpressure;
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
    this.sensitiveResponseBody = sensitiveResponseBody;
  }

  BackpressureCall backpressure() {
    return backpressure;
  }

  @Override
  public void completed(final ApiResponse<HttpT> result) {
    final ApiEntity<HttpT> body = result.entity();
    final int code = result.getCode();
    final String reason = result.getReasonPhrase();

    if (wasSuccessful(code)) {
      if (backpressure != null) {
        backpressure.onSuccess();
      }
      handleSuccessResponse(body, code, reason);
      return;
    }

    if (retryOnBackpressure(result)) {
      return;
    }

    handleErrorResponse(body, code, reason);
  }

  /**
   * Schedules another attempt if the response is a rejection that is worth retrying and the retry
   * policy allows it. Registers the rejection with the concurrency limit even if there is no retry
   * left.
   *
   * @return true if another attempt is scheduled, in which case the request is not completed yet
   */
  private boolean retryOnBackpressure(final ApiResponse<HttpT> result) {
    if (backpressure == null) {
      return false;
    }

    final ApiEntity<HttpT> body = result.entity();
    String title = null;
    String detail = null;
    if (body != null && body.isProblem() && body.problem() != null) {
      title = body.problem().getTitle();
      detail = body.problem().getDetail();
    }
    final Failure failure = BackpressureSignals.classifyHttp(result.getCode(), title, detail);
    final long delay = backpressure.onFailure(failure, parseRetryAfterMillis(result.retryAfter()));
    if (delay < 0) {
      return false;
    }

    backpressure.schedule(
        () -> {
          if (response.isDone()) {
            // the caller gave up in the meantime, e.g. cancelled or timed out the future
            return;
          }
          try {
            retryAction.run();
          } catch (final RuntimeException e) {
            // e.g. the client was closed in the meantime
            response.completeExceptionally(new ClientException(e));
          }
        },
        delay);
    return true;
  }

  /** Understands the delay-seconds form of {@code Retry-After}; anything else counts as absent. */
  private static long parseRetryAfterMillis(final String retryAfter) {
    if (retryAfter == null) {
      return 0;
    }
    try {
      final long seconds = Long.parseLong(retryAfter.trim());
      return seconds <= 0 ? 0 : Math.min(seconds, Long.MAX_VALUE / 1000) * 1000;
    } catch (final NumberFormatException e) {
      return 0;
    }
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
                  describeUnknownBody(body)),
              code,
              reason));
      return;
    }

    if (body.isResponse()) {
      response.completeExceptionally(
          new MalformedResponseException(
              String.format(
                  "Expected to receive a problem body, but got an actual response: %s",
                  describeResponseBody(body)),
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
                  describeUnknownBody(body)),
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

  /**
   * A body that the server sent under an unexpected content type is echoed to make the mismatch
   * debuggable, unless the endpoint answers with secret material: a cluster or proxy breaking the
   * content type contract would otherwise put every secret in the batch into an exception message,
   * and from there into the caller's logs.
   */
  private String describeUnknownBody(final ApiEntity<HttpT> body) {
    return sensitiveResponseBody
        ? REDACTED_BODY
        : StandardCharsets.UTF_8.decode(body.unknown()).toString();
  }

  /**
   * Same reasoning as {@link #describeUnknownBody(ApiEntity)}: the parsed body is only printable
   * when it cannot carry secret material, because the generated response types print all of their
   * fields.
   */
  private String describeResponseBody(final ApiEntity<HttpT> body) {
    return sensitiveResponseBody ? REDACTED_BODY : String.valueOf(body.response());
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
