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
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.probe.ProbeAnswer;
import io.camunda.client.api.worker.probe.ProbeExceptionHandler;
import io.camunda.client.api.worker.probe.ProbeExceptionHandler.ProbeExceptionHandlerContext;
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.impl.Loggers;
import io.camunda.client.impl.backpressure.BackpressureScope;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.worker.JobWorkerBuilderImpl;
import io.camunda.client.protocol.rest.ActivatedProbeResult;
import io.camunda.client.protocol.rest.ProbeActivationRequest;
import io.camunda.client.protocol.rest.ProbeActivationResult;
import io.camunda.client.protocol.rest.TenantFilterEnum;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/**
 * Polls for the probes of all its probe IDs in one request, runs the handler of each probe's probe
 * ID, and sends the answer, as the job worker polls for jobs:
 *
 * <ul>
 *   <li>A poll asks for as many probes as there are free slots, never more than {@code
 *       maxProbesActive}; without a free slot it does not poll.
 *   <li>After a poll that succeeded, the next one follows after {@code pollInterval}, or as soon as
 *       a slot frees up while no poll is scheduled.
 *   <li>After a poll that failed, including a 404 from a cluster without probes, the next one
 *       follows after the backoff. The worker never stops polling on its own.
 * </ul>
 */
final class ProbeWorkerImpl implements ProbeWorker, Closeable {

  private static final Logger LOG = Loggers.PROBE_WORKER_LOGGER;
  private static final BackoffSupplier DEFAULT_BACKOFF_SUPPLIER =
      JobWorkerBuilderImpl.DEFAULT_BACKOFF_SUPPLIER;
  private static final int SERVICE_UNAVAILABLE = 503;
  private static final ProbeExceptionHandler DEFAULT_EXCEPTION_HANDLER =
      ProbeExceptionHandler.createDefault();

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final Map<String, ProbeHandler> handlers;
  private final String workerName;
  private final List<String> tenantIds;
  private final TenantFilter tenantFilter;
  private final int maxProbesActive;
  private final long initialPollInterval;
  private final Duration requestTimeout;
  private final Duration requestTimeoutOffset;
  private final BackoffSupplier backoffSupplier;
  private final ProbeExceptionHandler exceptionHandler;
  private final ScheduledExecutorService scheduler;
  private final Executor handlerExecutor;
  private final ProbeAnswerSender answerSender;

  private final AtomicBoolean open = new AtomicBoolean(true);
  private final AtomicBoolean pollScheduled = new AtomicBoolean(false);
  private final AtomicBoolean polling = new AtomicBoolean(false);
  private final AtomicInteger activeProbes = new AtomicInteger(0);
  private volatile long pollInterval;

  ProbeWorkerImpl(
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final Map<String, ProbeHandler> handlers,
      final String workerName,
      final List<String> tenantIds,
      final TenantFilter tenantFilter,
      final int maxProbesActive,
      final Duration pollInterval,
      final Duration requestTimeout,
      final Duration requestTimeoutOffset,
      final BackoffSupplier backoffSupplier,
      final ProbeExceptionHandler exceptionHandler,
      final ScheduledExecutorService scheduler,
      final Executor handlerExecutor) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.handlers = handlers;
    this.workerName = workerName;
    this.tenantIds = tenantIds;
    this.tenantFilter = tenantFilter;
    this.maxProbesActive = maxProbesActive;
    initialPollInterval = pollInterval.toMillis();
    this.pollInterval = initialPollInterval;
    this.requestTimeout = requestTimeout;
    this.requestTimeoutOffset = requestTimeoutOffset;
    this.backoffSupplier = backoffSupplier;
    this.exceptionHandler = exceptionHandler;
    this.scheduler = scheduler;
    this.handlerExecutor = handlerExecutor;
    answerSender =
        new ProbeAnswerSender(
            httpClient, jsonMapper, scheduler, backoffSupplier, System::currentTimeMillis);
  }

  void open() {
    tryPoll();
  }

  @Override
  public boolean isOpen() {
    return open.get();
  }

  @Override
  public boolean isClosed() {
    return !open.get() && !polling.get() && activeProbes.get() == 0;
  }

  @Override
  public void close() {
    open.set(false);
  }

  private int freeCapacity() {
    return maxProbesActive - activeProbes.get();
  }

  private void schedulePoll() {
    if (open.get() && pollScheduled.compareAndSet(false, true)) {
      scheduler.schedule(this::onScheduledPoll, pollInterval, TimeUnit.MILLISECONDS);
    }
  }

  private void onScheduledPoll() {
    pollScheduled.set(false);
    tryPoll();
  }

  private void pollWhenCapacityAllows() {
    if (!pollScheduled.get()) {
      tryPoll();
    }
  }

  private void tryPoll() {
    if (!open.get() || freeCapacity() <= 0 || !polling.compareAndSet(false, true)) {
      return;
    }
    try {
      poll(freeCapacity());
    } catch (final Throwable e) {
      LOG.warn("Unexpected failure to activate probes", e);
      onPollError(e);
    }
  }

  private void poll(final int maxProbesToActivate) {
    LOG.trace(
        "Polling at max {} probes for worker {} and probe IDs {}",
        maxProbesToActivate,
        workerName,
        handlers.keySet());
    final ProbeActivationRequest request =
        new ProbeActivationRequest()
            .probeIds(new ArrayList<>(handlers.keySet()))
            .worker(workerName)
            .maxProbesToActivate(maxProbesToActivate)
            .requestTimeout(requestTimeout.toMillis())
            .tenantFilter(TenantFilterEnum.valueOf(tenantFilter.name()))
            .tenantIds(
                tenantFilter == TenantFilter.PROVIDED
                    ? new ArrayList<>(tenantIds)
                    : new ArrayList<>());
    final HttpCamundaFuture<ProbeActivationResult> result =
        new HttpCamundaFuture<>(BackpressureScope.UNMANAGED);
    // the response carries the injected secret values, so it must never reach an error message
    httpClient.postWithSensitiveResponse(
        "/probes/activation",
        jsonMapper.toJson(request),
        httpClient
            .newRequestConfig()
            .setResponseTimeout(
                requestTimeout.plus(requestTimeoutOffset).toMillis(), TimeUnit.MILLISECONDS)
            .build(),
        ProbeActivationResult.class,
        response -> response,
        result);
    result.whenComplete(
        (response, error) -> {
          if (error != null) {
            onPollError(error);
          } else {
            onPollSuccess(response);
          }
        });
  }

  private void onPollSuccess(final ProbeActivationResult response) {
    pollInterval = initialPollInterval;
    final List<ActivatedProbeResult> probes =
        response == null || response.getProbes() == null ? new ArrayList<>() : response.getProbes();
    probes.forEach(this::dispatch);
    if (!probes.isEmpty()) {
      LOG.debug("Activated {} probes for worker {}", probes.size(), workerName);
    }
    polling.set(false);
    schedulePoll();
  }

  private void onPollError(final Throwable error) {
    final long previousInterval = pollInterval;
    try {
      pollInterval = backoffSupplier.supplyRetryDelay(previousInterval);
    } catch (final Exception e) {
      LOG.warn(
          "Expected to supply retry delay, but an exception was thrown. Falling back to default "
              + "backoff supplier",
          e);
      pollInterval = DEFAULT_BACKOFF_SUPPLIER.supplyRetryDelay(previousInterval);
    }
    if (open.get()) {
      logPollFailure(error);
    }
    polling.set(false);
    schedulePoll();
  }

  private void logPollFailure(final Throwable error) {
    final Throwable cause = error.getCause() != null ? error.getCause() : error;
    if (isBackpressure(error) || isBackpressure(cause)) {
      // expected on a loaded cluster, and handled by the backoff, as for job workers
      LOG.trace(
          "Failed to activate probes for worker {}, retrying in {} ms",
          workerName,
          pollInterval,
          error);
      return;
    }
    LOG.warn(
        "Failed to activate probes for worker {} and probe IDs {}, retrying in {} ms",
        workerName,
        handlers.keySet(),
        pollInterval,
        error);
  }

  private static boolean isBackpressure(final Throwable error) {
    return error instanceof ClientHttpException
        && ((ClientHttpException) error).code() == SERVICE_UNAVAILABLE;
  }

  /**
   * Hands one probe to its handler. Never throws: a failure here would skip the rest of the
   * response, and the probe simply ends at its deadline, as if no worker had answered it.
   */
  private void dispatch(final ActivatedProbeResult result) {
    final ActivatedProbeImpl probe;
    try {
      probe = new ActivatedProbeImpl(jsonMapper, result);
    } catch (final RuntimeException e) {
      LOG.warn("Failed to read an activated probe for worker {}", workerName, e);
      return;
    }
    activeProbes.incrementAndGet();
    final AtomicBoolean slotHeld = new AtomicBoolean(true);
    try {
      handlerExecutor.execute(
          () -> {
            try {
              handle(probe);
            } finally {
              releaseSlot(slotHeld);
            }
          });
    } catch (final Throwable e) {
      releaseSlot(slotHeld);
      LOG.warn(
          "Failed to hand probe with key {} to its handler; it ends at its deadline",
          probe.getKey(),
          e);
    }
  }

  private void releaseSlot(final AtomicBoolean slotHeld) {
    if (slotHeld.compareAndSet(true, false)) {
      activeProbes.decrementAndGet();
      pollWhenCapacityAllows();
    }
  }

  private void handle(final ActivatedProbeImpl probe) {
    final ProbeHandler handler = handlers.get(probe.getProbeId());
    ProbeAnswer answer;
    if (handler == null) {
      LOG.warn(
          "Worker {} has no handler for probe with key {} of probe ID {}, answering UNSUPPORTED",
          workerName,
          probe.getKey(),
          probe.getProbeId());
      answer = ProbeAnswer.unsupported();
    } else {
      try {
        answer = handler.handle(probe);
        if (answer == null) {
          answer =
              answerToFailure(
                  probe, new IllegalStateException("The probe handler returned no answer."));
        }
      } catch (final Exception e) {
        answer = answerToFailure(probe, e);
      }
    }
    answerSender.send(probe, answer);
  }

  private ProbeAnswer answerToFailure(final ActivatedProbeImpl probe, final Exception failure) {
    final ProbeExceptionHandlerContext context = new ProbeExceptionHandlerContext(probe, failure);
    try {
      final ProbeAnswer answer = exceptionHandler.handleProbeException(context);
      if (answer != null) {
        return answer;
      }
      LOG.warn("The probeExceptionHandler returned no answer, using the default handler");
    } catch (final Exception e) {
      LOG.warn(
          "The probeExceptionHandler failed, using the default handler: {}",
          ProbeExceptionHandlerImpl.stackTraceWithoutSecrets(probe, e));
    }
    return DEFAULT_EXCEPTION_HANDLER.handleProbeException(context);
  }
}
