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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1.ProbeWorkerBuilderStep2;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1.ProbeWorkerBuilderStep3;
import io.camunda.client.impl.http.HttpClient;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class ProbeWorkerBuilderImpl
    implements ProbeWorkerBuilderStep1, ProbeWorkerBuilderStep2, ProbeWorkerBuilderStep3 {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final ScheduledExecutorService scheduler;
  private final List<Closeable> closeables;

  private String probeId;
  private ProbeHandler handler;
  private String workerName;
  private List<String> tenantIds;
  private int maxProbesActive = 4;
  private Duration pollInterval = Duration.ofMillis(100);
  private Duration requestTimeout = Duration.ofSeconds(10);
  private BackoffSupplier backoffSupplier = BackoffSupplier.newBackoffBuilder().build();

  public ProbeWorkerBuilderImpl(
      final CamundaClientConfiguration config,
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final ScheduledExecutorService scheduler,
      final List<Closeable> closeables) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.scheduler = scheduler;
    this.closeables = closeables;
    workerName = config.getDefaultJobWorkerName();
    tenantIds = config.getDefaultJobWorkerTenantIds();
  }

  @Override
  public ProbeWorkerBuilderStep2 probeId(final String probeId) {
    this.probeId = Objects.requireNonNull(probeId, "probeId");
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep3 handler(final ProbeHandler handler) {
    this.handler = Objects.requireNonNull(handler, "handler");
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep3 name(final String workerName) {
    this.workerName = workerName;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep3 tenantIds(final List<String> tenantIds) {
    this.tenantIds = new ArrayList<>(tenantIds);
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep3 tenantIds(final String... tenantIds) {
    return tenantIds(Arrays.asList(tenantIds));
  }

  @Override
  public ProbeWorkerBuilderStep3 maxProbesActive(final int maxProbesActive) {
    this.maxProbesActive = maxProbesActive;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep3 pollInterval(final Duration pollInterval) {
    this.pollInterval = pollInterval;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep3 requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep3 backoffSupplier(final BackoffSupplier backoffSupplier) {
    this.backoffSupplier = backoffSupplier;
    return this;
  }

  @Override
  public ProbeWorker open() {
    final ProbeWorkerImpl worker =
        new ProbeWorkerImpl(
            httpClient,
            jsonMapper,
            probeId,
            workerName,
            tenantIds,
            handler,
            scheduler,
            Executors.newFixedThreadPool(maxProbesActive),
            maxProbesActive,
            pollInterval,
            requestTimeout,
            backoffSupplier);
    closeables.add(worker::close);
    worker.open();
    return worker;
  }
}
