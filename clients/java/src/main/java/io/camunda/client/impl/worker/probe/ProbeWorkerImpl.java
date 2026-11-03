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
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.impl.Loggers;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/**
 * Polls {@code POST /v2/probes/activation} for one probe ID, as the job worker polls for one job
 * type, hands each probe to the handler and sends the answer over REST, exactly once. A failed
 * poll, including a 404 from a cluster without probes, is retried with the backoff supplier.
 */
public final class ProbeWorkerImpl implements ProbeWorker {

  private static final Logger LOGGER = Loggers.JOB_WORKER_LOGGER;
  private static final int MIN_REDACTED_LENGTH = 4;

  /** How much longer the HTTP request waits than the long poll it asks for. */
  private static final long RESPONSE_TIMEOUT_OFFSET_MS = 10_000;

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final String probeId;
  private final String workerName;
  private final List<String> tenantIds;
  private final ProbeHandler handler;
  private final ScheduledExecutorService scheduler;
  private final ExecutorService handlerExecutor;
  private final int maxProbesActive;
  private final Duration pollInterval;
  private final Duration requestTimeout;
  private final BackoffSupplier backoffSupplier;
  private final AtomicInteger activeProbes = new AtomicInteger();

  private volatile boolean closed;
  private long retryDelay;

  ProbeWorkerImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final String probeId,
      final String workerName,
      final List<String> tenantIds,
      final ProbeHandler handler,
      final ScheduledExecutorService scheduler,
      final ExecutorService handlerExecutor,
      final int maxProbesActive,
      final Duration pollInterval,
      final Duration requestTimeout,
      final BackoffSupplier backoffSupplier) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.probeId = probeId;
    this.workerName = workerName;
    this.tenantIds = tenantIds;
    this.handler = handler;
    this.scheduler = scheduler;
    this.handlerExecutor = handlerExecutor;
    this.maxProbesActive = maxProbesActive;
    this.pollInterval = pollInterval;
    this.requestTimeout = requestTimeout;
    this.backoffSupplier = backoffSupplier;
  }

  void open() {
    schedulePoll(0);
  }

  @Override
  public boolean isOpen() {
    return !closed;
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public void close() {
    closed = true;
    handlerExecutor.shutdownNow();
  }

  private void schedulePoll(final long delayMs) {
    if (!closed) {
      scheduler.schedule(this::poll, delayMs, TimeUnit.MILLISECONDS);
    }
  }

  private void poll() {
    if (closed) {
      return;
    }
    final int capacity = maxProbesActive - activeProbes.get();
    if (capacity <= 0) {
      schedulePoll(pollInterval.toMillis());
      return;
    }

    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("probeId", probeId);
    body.put("worker", workerName);
    body.put("maxProbesToActivate", capacity);
    body.put("requestTimeout", requestTimeout.toMillis());
    body.put("tenantIds", tenantIds);
    final HttpCamundaFuture<Map> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/probes/activation",
        jsonMapper.toJson(body),
        httpClient
            .newRequestConfig()
            .setResponseTimeout(
                requestTimeout.toMillis() + RESPONSE_TIMEOUT_OFFSET_MS, TimeUnit.MILLISECONDS)
            .build(),
        Map.class,
        response -> response,
        result);
    result.whenComplete(
        (response, error) -> {
          if (closed) {
            return;
          }
          if (error != null) {
            retryDelay = backoffSupplier.supplyRetryDelay(retryDelay);
            LOGGER.debug(
                "Failed to poll probes of '{}'; retrying in {} ms", probeId, retryDelay, error);
            schedulePoll(retryDelay);
            return;
          }
          retryDelay = 0;
          final List<?> probes = probesOf(response);
          for (final Object probe : probes) {
            final ActivatedProbe activated = new ActivatedProbeImpl(jsonMapper, (Map<?, ?>) probe);
            activeProbes.incrementAndGet();
            handlerExecutor.execute(
                () -> {
                  try {
                    handle(activated);
                  } finally {
                    activeProbes.decrementAndGet();
                  }
                });
          }
          // a long poll that returned probes may have more waiting: poll again right away
          schedulePoll(probes.isEmpty() ? pollInterval.toMillis() : 0);
        });
  }

  private static List<?> probesOf(final Map<?, ?> response) {
    final Object probes = response == null ? null : response.get("probes");
    return probes instanceof List ? (List<?>) probes : Collections.emptyList();
  }

  private void handle(final ActivatedProbe probe) {
    ProbeAnswer answer;
    try {
      answer = handler.handle(probe);
      if (answer == null) {
        answer = ProbeAnswer.error("The probe handler returned no answer.");
      }
    } catch (final Exception e) {
      answer = ProbeAnswer.error(redact(String.valueOf(e.getMessage()), probe));
    }
    sendAnswer(probe.getKey(), answer);
  }

  private void sendAnswer(final long probeKey, final ProbeAnswer answer) {
    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("outcome", answer.getOutcome().name());
    if (answer.getData() != null) {
      body.put("data", answer.getData());
    }
    if (answer.getMessage() != null) {
      body.put("message", answer.getMessage());
    }
    final HttpCamundaFuture<Void> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/probes/" + probeKey + "/answer",
        jsonMapper.toJson(body),
        httpClient.newRequestConfig().build(),
        ignored -> null,
        result);
    result.whenComplete(
        (ok, error) -> {
          if (error != null) {
            // a late answer gets 404: the probe ended at its deadline
            LOGGER.debug("Failed to answer probe {}", probeKey, error);
          }
        });
  }

  /**
   * Removes the input's text values from the message. The worker cannot tell which of them were
   * injected secrets, so it removes every one long enough to be one.
   */
  private static String redact(final String message, final ActivatedProbe probe) {
    String redacted = message;
    for (final String value : textValues(probe.getInputAsMap())) {
      if (value.length() >= MIN_REDACTED_LENGTH) {
        redacted = redacted.replace(value, "***");
      }
    }
    return redacted;
  }

  private static List<String> textValues(final Object node) {
    final List<String> values = new ArrayList<>();
    if (node instanceof String) {
      values.add((String) node);
    } else if (node instanceof Map) {
      ((Map<?, ?>) node).values().forEach(value -> values.addAll(textValues(value)));
    } else if (node instanceof List) {
      ((List<?>) node).forEach(value -> values.addAll(textValues(value)));
    }
    return values;
  }
}
