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
package io.camunda.client.impl.worker.probe;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.impl.Loggers;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.worker.JobWorkerBuilderImpl;
import io.camunda.client.protocol.rest.ProbeAnswerRequest;
import io.camunda.client.protocol.rest.ProbeErrorAnswerRequest;
import io.camunda.client.protocol.rest.ProbeOkAnswerRequest;
import io.camunda.client.protocol.rest.ProbeUnsupportedAnswerRequest;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

/**
 * Sends the answer to a probe. A probe has a short deadline and its client waits for this answer,
 * so a send that failed for a reason that may pass is tried again until the deadline:
 *
 * <ul>
 *   <li>503 (backpressure) or a network error: sent again after the backoff, until the deadline.
 *   <li>413 (the answer does not fit in one log batch): a short {@code ERROR} is sent instead, so
 *       the client gets an answer at once instead of waiting for the deadline.
 *   <li>404: the probe ended, most likely at its deadline; the answer came too late.
 *   <li>Anything else (400, 403, 409, ...): sending again would fail the same way.
 * </ul>
 */
final class ProbeAnswerSender {

  static final String TOO_LARGE_MESSAGE = "The answer is too large.";
  static final String DATA_NOT_AN_OBJECT_MESSAGE =
      "The probe handler answered with data that is not a JSON object.";

  private static final Logger LOG = Loggers.PROBE_WORKER_LOGGER;
  private static final int SERVICE_UNAVAILABLE = 503;
  private static final int NOT_FOUND = 404;
  private static final int PAYLOAD_TOO_LARGE = 413;

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final ScheduledExecutorService scheduler;
  private final BackoffSupplier backoffSupplier;
  private final LongSupplier clock;

  ProbeAnswerSender(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final ScheduledExecutorService scheduler,
      final BackoffSupplier backoffSupplier,
      final LongSupplier clock) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.scheduler = scheduler;
    this.backoffSupplier = backoffSupplier;
    this.clock = clock;
  }

  void send(final ActivatedProbe probe, final ProbeAnswer answer) {
    final ProbeAnswerRequest request;
    try {
      request = toRequest(answer);
    } catch (final RuntimeException e) {
      LOG.warn(
          "Expected the answer to probe with key {} to carry a JSON object as data, but it could "
              + "not be converted; answering ERROR instead: {}",
          probe.getKey(),
          ProbeExceptionHandlerImpl.stackTraceWithoutSecrets(probe, e));
      send(
          probe,
          new ProbeErrorAnswerRequest().outcome("ERROR").message(DATA_NOT_AN_OBJECT_MESSAGE),
          0,
          true);
      return;
    }
    send(probe, request, 0, false);
  }

  private void send(
      final ActivatedProbe probe,
      final ProbeAnswerRequest request,
      final long previousDelay,
      final boolean isFallback) {
    final HttpCamundaFuture<Void> result = new HttpCamundaFuture<>();
    try {
      httpClient.post(
          "/probes/" + probe.getKey() + "/answer",
          jsonMapper.toJson(request),
          httpClient.newRequestConfig().build(),
          r -> null,
          result);
    } catch (final RuntimeException e) {
      result.completeExceptionally(e);
    }
    result.whenComplete(
        (ignored, error) -> {
          if (error != null) {
            onFailure(probe, request, previousDelay, isFallback, unwrap(error));
          }
        });
  }

  private void onFailure(
      final ActivatedProbe probe,
      final ProbeAnswerRequest request,
      final long previousDelay,
      final boolean isFallback,
      final Throwable error) {
    if (error instanceof ClientHttpException) {
      final int code = ((ClientHttpException) error).code();
      if (code == PAYLOAD_TOO_LARGE && !isFallback) {
        LOG.warn(
            "The answer to probe with key {} of probe ID {} is too large for the cluster; "
                + "answering ERROR instead",
            probe.getKey(),
            probe.getProbeId());
        send(
            probe,
            new ProbeErrorAnswerRequest().outcome("ERROR").message(TOO_LARGE_MESSAGE),
            0,
            true);
        return;
      }
      if (code == NOT_FOUND) {
        LOG.debug(
            "Failed to answer probe with key {}: it ended, most likely at its deadline",
            probe.getKey(),
            error);
        return;
      }
      if (code != SERVICE_UNAVAILABLE) {
        LOG.warn("Failed to answer probe with key {}; not retried", probe.getKey(), error);
        return;
      }
    }

    final long delay = retryDelay(previousDelay);
    if (clock.getAsLong() + delay >= probe.getDeadline()) {
      LOG.warn(
          "Failed to answer probe with key {} before its deadline; giving up",
          probe.getKey(),
          error);
      return;
    }
    LOG.debug(
        "Failed to answer probe with key {}; retrying in {} ms", probe.getKey(), delay, error);
    try {
      scheduler.schedule(
          () -> resend(probe, request, delay, isFallback), delay, TimeUnit.MILLISECONDS);
    } catch (final RejectedExecutionException e) {
      LOG.debug("Failed to schedule the answer to probe with key {} again", probe.getKey(), e);
    }
  }

  /**
   * Sends the answer again, unless the scheduler ran this retry after the deadline. The first send
   * does not check the deadline: it is the broker's clock, and a client clock ahead of it would
   * drop answers the cluster still takes.
   */
  private void resend(
      final ActivatedProbe probe,
      final ProbeAnswerRequest request,
      final long previousDelay,
      final boolean isFallback) {
    if (clock.getAsLong() >= probe.getDeadline()) {
      LOG.warn("Failed to answer probe with key {} before its deadline; giving up", probe.getKey());
      return;
    }
    send(probe, request, previousDelay, isFallback);
  }

  private long retryDelay(final long previousDelay) {
    try {
      return backoffSupplier.supplyRetryDelay(previousDelay);
    } catch (final Exception e) {
      LOG.warn(
          "Expected to supply retry delay, but an exception was thrown. Falling back to default "
              + "backoff supplier",
          e);
      return JobWorkerBuilderImpl.DEFAULT_BACKOFF_SUPPLIER.supplyRetryDelay(previousDelay);
    }
  }

  @SuppressWarnings("unchecked")
  private ProbeAnswerRequest toRequest(final ProbeAnswer answer) {
    switch (answer.getOutcome()) {
      case OK:
        final Object data = answer.getData();
        final Map<String, Object> map =
            data instanceof Map
                ? (Map<String, Object>) data
                : jsonMapper.fromJsonAsMap(jsonMapper.toJson(data));
        return new ProbeOkAnswerRequest().outcome("OK").data(map);
      case UNSUPPORTED:
        return new ProbeUnsupportedAnswerRequest().outcome("UNSUPPORTED");
      case ERROR:
        return new ProbeErrorAnswerRequest().outcome("ERROR").message(answer.getMessage());
      default:
        throw new IllegalStateException("Unexpected outcome: " + answer.getOutcome());
    }
  }

  private static Throwable unwrap(final Throwable error) {
    return error instanceof CompletionException && error.getCause() != null
        ? error.getCause()
        : error;
  }
}
