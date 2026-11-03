/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeAnswerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeCreateRequest;
import io.camunda.zeebe.gateway.impl.probe.LongPollingActivateProbesHandler;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Creates probes and records their answers. Creating a probe waits for its end, so the request
 * times out a little after the probe's own deadline: the client gets the engine's answer rather
 * than a gateway timeout.
 */
public final class ProbeServices extends PhysicalTenantScopedApiServices<ProbeServices> {

  /** The cluster request timeout, used when the client gives none. */
  public static final long DEFAULT_REQUEST_TIMEOUT_MS = Duration.ofSeconds(15).toMillis();

  public static final long MAX_REQUEST_TIMEOUT_MS = Duration.ofSeconds(30).toMillis();

  /** How much longer the gateway waits than the probe's deadline. */
  static final long RESPONSE_OFFSET_MS = 500;

  private ProbeServices(final ExpressionServices source) {
    super(
        source.getPhysicalTenantId(),
        source.brokerClient,
        source.securityContextProvider,
        source.executorProvider,
        source.brokerRequestAuthorizationConverter);
  }

  /**
   * Builds the probe services of a physical tenant from another service of the same tenant, which
   * carries the same broker client, executor and authorization converter. A POC shortcut that keeps
   * probes out of the service registry.
   */
  public static ProbeServices of(final ExpressionServices source) {
    return new ProbeServices(source);
  }

  public CompletableFuture<ProbeResult> createProbe(
      final ProbeCreateRequest request, final CamundaAuthentication authentication) {
    final long timeout =
        request.requestTimeout() == null || request.requestTimeout() == 0
            ? DEFAULT_REQUEST_TIMEOUT_MS
            : request.requestTimeout();
    // the deadline is fixed here, when the request arrives, and the engine uses it as is: time
    // spent before the engine processes CREATE comes out of the probe's time, never out of the
    // margin the gateway waits beyond it, so the engine's answer arrives first
    final long deadline = System.currentTimeMillis() + timeout;
    final var brokerRequest =
        new BrokerProbeCreateRequest()
            .setProbeId(request.probeId())
            .setInputExpression(request.input())
            .setTenantId(request.tenantId())
            .setRequestTimeout(timeout)
            .setDeadline(deadline);
    return sendBrokerRequestWithFullResponse(
            brokerRequest, authentication, Duration.ofMillis(timeout + RESPONSE_OFFSET_MS))
        .thenApply(response -> new ProbeResult(response.getKey(), response.getResponse()));
  }

  public CompletableFuture<ProbeRecord> answerProbe(
      final ProbeAnswerRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerProbeAnswerRequest(request.probeKey())
            .setOutcome(request.outcome())
            .setData(request.data())
            .setMessage(request.message()),
        authentication);
  }

  /**
   * Collects probes for a worker's poll through the gateway's long polling handler, which sends the
   * poll to every partition and holds it until a probe arrives or the request times out.
   *
   * @param deliver hands the collected probes to the worker; returns whether it got them, so the
   *     handler can yield the probes it did not
   */
  public void activateProbes(
      final LongPollingActivateProbesHandler handler,
      final ProbeActivationRequest request,
      final CamundaAuthentication authentication,
      final Predicate<ProbeBatchRecord> deliver,
      final Consumer<Throwable> onError) {
    final var brokerRequest =
        new BrokerActivateProbesRequest(
            request.probeIds(),
            request.worker(),
            request.maxProbesToActivate(),
            request.tenantIds(),
            request.tenantFilter());
    applyBrokerRequestMutators(brokerRequest, authentication);
    handler.activateProbes(
        brokerRequest,
        request.requestTimeout() == null ? 0 : request.requestTimeout(),
        deliver,
        error -> onError.accept(ErrorMapper.mapError(error)));
  }

  public record ProbeActivationRequest(
      List<String> probeIds,
      String worker,
      int maxProbesToActivate,
      Long requestTimeout,
      List<String> tenantIds,
      TenantFilter tenantFilter) {}

  public record ProbeCreateRequest(
      String probeId, String input, String tenantId, Long requestTimeout) {}

  public record ProbeAnswerRequest(
      long probeKey, ProbeOutcome outcome, Map<String, Object> data, String message) {}

  /** How the probe ended: the record carries the answer, or the reason it ended without one. */
  public record ProbeResult(long probeKey, ProbeRecord probe) {}
}
