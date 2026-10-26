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
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.impl.Loggers;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamProbesRequest;
import io.grpc.Context;
import io.grpc.Context.CancellableContext;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * Keeps one probe stream open, opening it again after a failure, hands each pushed probe to the
 * handler and sends the answer over REST, exactly once.
 */
public final class ProbeWorkerImpl implements ProbeWorker {

  private static final Logger LOGGER = Loggers.JOB_WORKER_LOGGER;
  private static final int MIN_REDACTED_LENGTH = 4;

  private final GatewayStub asyncStub;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final StreamProbesRequest request;
  private final ProbeHandler handler;
  private final ScheduledExecutorService scheduler;
  private final ExecutorService handlerExecutor;
  private final Duration retryDelay;

  private volatile boolean closed;
  private volatile CancellableContext streamContext;

  ProbeWorkerImpl(
      final GatewayStub asyncStub,
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final StreamProbesRequest request,
      final ProbeHandler handler,
      final ScheduledExecutorService scheduler,
      final ExecutorService handlerExecutor,
      final Duration retryDelay) {
    this.asyncStub = asyncStub;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.request = request;
    this.handler = handler;
    this.scheduler = scheduler;
    this.handlerExecutor = handlerExecutor;
    this.retryDelay = retryDelay;
  }

  void open() {
    if (closed) {
      return;
    }
    final CancellableContext context = Context.current().withCancellation();
    streamContext = context;
    context.run(() -> asyncStub.streamProbes(request, new ProbeStreamObserver()));
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
    final CancellableContext context = streamContext;
    if (context != null) {
      context.cancel(null);
    }
    handlerExecutor.shutdownNow();
  }

  private void reopenLater(final Throwable error) {
    if (closed) {
      return;
    }
    LOGGER.debug(
        "Probe stream '{}:{}' closed; opening it again in {}",
        request.getOperation(),
        request.getTemplate(),
        retryDelay,
        error);
    scheduler.schedule(this::open, retryDelay.toMillis(), TimeUnit.MILLISECONDS);
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
            LOGGER.warn("Failed to answer probe {}", probeKey, error);
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

  private final class ProbeStreamObserver
      implements StreamObserver<GatewayOuterClass.ActivatedProbe> {

    @Override
    public void onNext(final GatewayOuterClass.ActivatedProbe value) {
      final ActivatedProbe probe = new ActivatedProbeImpl(jsonMapper, value);
      handlerExecutor.execute(() -> handle(probe));
    }

    @Override
    public void onError(final Throwable error) {
      reopenLater(error);
    }

    @Override
    public void onCompleted() {
      reopenLater(null);
    }
  }
}
