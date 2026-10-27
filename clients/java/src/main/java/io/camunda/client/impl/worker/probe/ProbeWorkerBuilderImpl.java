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
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.client.api.worker.probe.ProbeOperation;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1.ProbeWorkerBuilderStep2;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProbeCapability;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamProbesRequest;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class ProbeWorkerBuilderImpl
    implements ProbeWorkerBuilderStep1, ProbeWorkerBuilderStep2 {

  private final GatewayStub asyncStub;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final ScheduledExecutorService scheduler;
  private final List<Closeable> closeables;

  private final Map<String, ProbeHandler> handlers = new LinkedHashMap<>();
  private final List<ProbeCapability> capabilities = new ArrayList<>();
  private String workerName;
  private List<String> tenantIds;
  private int maxProbesActive = 4;
  private Duration retryDelay = Duration.ofSeconds(1);

  public ProbeWorkerBuilderImpl(
      final CamundaClientConfiguration config,
      final GatewayStub asyncStub,
      final HttpClient httpClient,
      final JsonMapper jsonMapper,
      final ScheduledExecutorService scheduler,
      final List<Closeable> closeables) {
    this.asyncStub = asyncStub;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    this.scheduler = scheduler;
    this.closeables = closeables;
    workerName = config.getDefaultJobWorkerName();
    tenantIds = config.getDefaultJobWorkerTenantIds();
  }

  @Override
  public ProbeWorkerBuilderStep2 capability(
      final ProbeOperation operation, final String template, final ProbeHandler handler) {
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(template, "template");
    Objects.requireNonNull(handler, "handler");
    final String capability = ProbeWorkerImpl.capability(operation.name(), template);
    if (handlers.putIfAbsent(capability, handler) != null) {
      throw new IllegalArgumentException(
          String.format(
              "Expected each capability once, but '%s' was registered twice", capability));
    }
    capabilities.add(
        ProbeCapability.newBuilder().setOperation(operation.name()).setTemplate(template).build());
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 name(final String workerName) {
    this.workerName = workerName;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 tenantIds(final List<String> tenantIds) {
    this.tenantIds = new ArrayList<>(tenantIds);
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 tenantIds(final String... tenantIds) {
    return tenantIds(Arrays.asList(tenantIds));
  }

  @Override
  public ProbeWorkerBuilderStep2 maxProbesActive(final int maxProbesActive) {
    this.maxProbesActive = maxProbesActive;
    return this;
  }

  @Override
  public ProbeWorkerBuilderStep2 streamRetryDelay(final Duration retryDelay) {
    this.retryDelay = retryDelay;
    return this;
  }

  @Override
  public ProbeWorker open() {
    final StreamProbesRequest request =
        StreamProbesRequest.newBuilder()
            .addAllCapabilities(capabilities)
            .setWorker(workerName)
            .addAllTenantIds(tenantIds)
            .build();
    final ProbeWorkerImpl worker =
        new ProbeWorkerImpl(
            asyncStub,
            httpClient,
            jsonMapper,
            request,
            Collections.unmodifiableMap(new LinkedHashMap<>(handlers)),
            scheduler,
            Executors.newFixedThreadPool(maxProbesActive),
            retryDelay);
    closeables.add(worker::close);
    worker.open();
    return worker;
  }
}
