/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeAnswerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.value.ProbeOperation;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    final var brokerRequest =
        new BrokerProbeCreateRequest()
            .setOperation(request.operation())
            .setTemplate(request.template())
            .setInputExpression(request.input())
            .setTenantId(request.tenantId())
            .setRequestTimeout(timeout);
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

  public record ProbeCreateRequest(
      ProbeOperation operation,
      String template,
      String input,
      String tenantId,
      Long requestTimeout) {}

  public record ProbeAnswerRequest(
      long probeKey, ProbeOutcome outcome, Map<String, Object> data, String message) {}

  /** How the probe ended: the record carries the answer, or the reason it ended without one. */
  public record ProbeResult(long probeKey, ProbeRecord probe) {}
}
