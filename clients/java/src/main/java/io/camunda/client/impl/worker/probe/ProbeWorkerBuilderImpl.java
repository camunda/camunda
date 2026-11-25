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

import static io.camunda.client.impl.command.ArgumentUtil.ensureGreaterThan;
import static io.camunda.client.impl.command.ArgumentUtil.ensureNotNull;
import static io.camunda.client.impl.command.ArgumentUtil.ensureNotNullNorEmpty;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.probe.ProbeExceptionHandler;
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1.ProbeWorkerBuilderStep2;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.worker.JobWorkerBuilderImpl;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Builds a probe worker. Its defaults are the job worker's: the client's default job worker name,
 * tenants, tenant filter, {@code maxJobsActive}, poll interval and request timeout, and the job
 * worker's exponential backoff. It runs its handlers on the client's job handling executor.
 */
public final class ProbeWorkerBuilderImpl
    implements ProbeWorkerBuilderStep1, ProbeWorkerBuilderStep2 {

  /** As the cluster accepts them: 1 to 256 characters, without whitespace, and not "*". */
  static final int MAX_PROBE_ID_LENGTH = 256;

  /** The most probe IDs the cluster accepts in one poll. */
  static final int MAX_PROBE_IDS = 100;

  private final CamundaClientConfiguration configuration;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final ScheduledExecutorService scheduledExecutor;
  private final ExecutorService handlerExecutor;
  private final List<Closeable> closeables;

  private final Map<String, ProbeHandler> handlers = new LinkedHashMap<>();
  private final List<String> defaultTenantIds;
  private final List<String> customTenantIds = new ArrayList<>();
  private String workerName;
  private TenantFilter tenantFilter;
  private int maxProbesActive;
  private Duration pollInterval;
  private Duration requestTimeout;
  private BackoffSupplier backoffSupplier = JobWorkerBuilderImpl.DEFAULT_BACKOFF_SUPPLIER;
  private ProbeExceptionHandler probeExceptionHandler = ProbeExceptionHandler.createDefault();

  public ProbeWorkerBuilderImpl(
      final CamundaClientConfiguration configuration,
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final ScheduledExecutorService scheduledExecutor,
      final ExecutorService handlerExecutor,
      final List<Closeable> closeables) {
    this.configuration = configuration;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.scheduledExecutor = scheduledExecutor;
    this.handlerExecutor = handlerExecutor;
    this.closeables = closeables;

    workerName = configuration.getDefaultJobWorkerName();
    defaultTenantIds = configuration.getDefaultJobWorkerTenantIds();
    tenantFilter = configuration.getDefaultJobWorkerTenantFilter();
    maxProbesActive = configuration.getDefaultJobWorkerMaxJobsActive();
    pollInterval = configuration.getDefaultJobPollInterval();
    requestTimeout = configuration.getDefaultRequestTimeout();
  }

  @Override
  public ProbeWorkerBuilderStep2 probeId(final String probeId, final ProbeHandler handler) {
    ensureValidProbeId(probeId);
    ensureNotNull("handler", handler);
    if (handlers.containsKey(probeId)) {
      throw new IllegalArgumentException(
          String.format("Expected each probe ID once, but '%s' was added twice", probeId));
    }
    handlers.put(probeId, handler);
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 name(final String workerName) {
    this.workerName = workerName;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 tenantIds(final List<String> tenantIds) {
    customTenantIds.addAll(tenantIds);
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 tenantIds(final String... tenantIds) {
    return tenantIds(Arrays.asList(tenantIds));
  }

  @Override
  public ProbeWorkerBuilderStep2 tenantFilter(final TenantFilter tenantFilter) {
    this.tenantFilter = tenantFilter;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 maxProbesActive(final int maxProbesActive) {
    this.maxProbesActive = maxProbesActive;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 pollInterval(final Duration pollInterval) {
    this.pollInterval = pollInterval;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 backoffSupplier(final BackoffSupplier backoffSupplier) {
    this.backoffSupplier = backoffSupplier;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 probeExceptionHandler(
      final ProbeExceptionHandler probeExceptionHandler) {
    this.probeExceptionHandler = probeExceptionHandler;
    return this;
  }

  @Override
  public ProbeWorker open() {
    ensureNotNullNorEmpty("workerName", workerName);
    ensureGreaterThan("maxProbesActive", maxProbesActive, 0);
    ensureNotNull("pollInterval", pollInterval);
    ensureNotNull("requestTimeout", requestTimeout);
    ensureNotNull("backoffSupplier", backoffSupplier);
    ensureNotNull("probeExceptionHandler", probeExceptionHandler);
    ensureNotNull("tenantFilter", tenantFilter);
    if (handlers.size() > MAX_PROBE_IDS) {
      throw new IllegalArgumentException(
          String.format(
              "Expected at most %d probe IDs per worker, but got %d",
              MAX_PROBE_IDS, handlers.size()));
    }

    final ProbeWorkerImpl worker =
        new ProbeWorkerImpl(
            httpClient,
            jsonMapper,
            new LinkedHashMap<>(handlers),
            workerName,
            customTenantIds.isEmpty() ? defaultTenantIds : new ArrayList<>(customTenantIds),
            tenantFilter,
            maxProbesActive,
            pollInterval,
            requestTimeout,
            configuration.getDefaultRequestTimeoutOffset(),
            backoffSupplier,
            probeExceptionHandler,
            scheduledExecutor,
            handlerExecutor);
    closeables.add(worker);
    worker.open();
    return worker;
  }

  private static void ensureValidProbeId(final String probeId) {
    ensureNotNullNorEmpty("probeId", probeId);
    if (probeId.length() > MAX_PROBE_ID_LENGTH
        || "*".equals(probeId)
        || probeId.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected a probe ID of 1 to %d characters, without whitespace, and not '*', but "
                  + "got '%s'",
              MAX_PROBE_ID_LENGTH, probeId));
    }
  }
}
