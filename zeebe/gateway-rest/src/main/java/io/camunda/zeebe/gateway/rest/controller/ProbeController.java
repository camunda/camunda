/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.ProbeServices;
import io.camunda.service.ProbeServices.ProbeActivationRequest;
import io.camunda.service.ProbeServices.ProbeAnswerRequest;
import io.camunda.service.ProbeServices.ProbeCreateRequest;
import io.camunda.service.ProbeServices.ProbeResult;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.impl.probe.LongPollingActivateProbesHandler;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.async.DeferredResult;

/**
 * Probes (alpha): a client asks a worker a short question and waits for the answer in the same
 * request; the worker answers over REST. The request and response bodies are plain records rather
 * than OpenAPI models, a POC shortcut.
 */
// hidden from the OpenAPI alignment: the POC has no spec operations for probes
@Hidden
@CamundaRestController
@RequestMapping("/v2/probes")
public class ProbeController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ObjectProvider<LongPollingActivateProbesHandler> activateProbesHandler;

  public ProbeController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final ObjectProvider<LongPollingActivateProbesHandler> activateProbesHandler) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    this.activateProbesHandler = activateProbesHandler;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createProbe(
      @PhysicalTenantId final String physicalTenantId, @RequestBody final CreateProbeBody body) {
    final var invalid = validate(body);
    if (invalid != null) {
      return RestErrorMapper.mapProblemToCompletedResponse(
          ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, invalid));
    }
    final var request =
        new ProbeCreateRequest(
            body.probeId(), body.input(), body.tenantId(), body.requestTimeout());
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
            () -> probeServices(physicalTenantId).createProbe(request, authentication),
            ProbeController::toResponse)
        .thenApply(ProbeController::titleNoWorkerRejection);
  }

  /**
   * A probe no worker can take at all is rejected at create; the rejection keeps its generic title,
   * so it is named here as the end of a probe would be.
   */
  private static ResponseEntity<Object> titleNoWorkerRejection(
      final ResponseEntity<Object> response) {
    if (response.getStatusCode() == HttpStatus.NOT_FOUND
        && response.getBody() instanceof final ProblemDetail problem
        && problem.getDetail() != null
        && problem.getDetail().contains("No worker can answer")) {
      problem.setTitle(ProbeEndReason.NO_WORKER.name());
    }
    return response;
  }

  /**
   * A worker's poll (long polling, as job activation): answers with the probes collected for the
   * probe ID, with their secret values injected, or with none when the request timed out.
   */
  @CamundaPostMapping(path = "/activation")
  public DeferredResult<ResponseEntity<Object>> activateProbes(
      @PhysicalTenantId final String physicalTenantId, @RequestBody final ActivateProbesBody body) {
    if (body.probeId() == null || body.probeId().isBlank()) {
      return badRequest("Expected a probe ID, but none was given.");
    }
    if (body.maxProbesToActivate() == null || body.maxProbesToActivate() <= 0) {
      return badRequest(
          "Expected to activate at least one probe, but max probes to activate was %s."
              .formatted(body.maxProbesToActivate()));
    }
    final var tenantFilter =
        body.tenantFilter() == null
            ? TenantFilter.PROVIDED
            : TenantFilter.valueOf(body.tenantFilter());
    final var request =
        new ProbeActivationRequest(
            body.probeId(),
            body.worker() == null ? "" : body.worker(),
            body.maxProbesToActivate(),
            body.requestTimeout(),
            body.tenantIds() == null ? List.of() : body.tenantIds(),
            tenantFilter);

    final var result = new CompletableFuture<ResponseEntity<Object>>();
    probeServices(physicalTenantId)
        .activateProbes(
            activateProbesHandler.getObject(),
            request,
            authenticationProvider.getCamundaAuthentication(),
            // false when the request already ended: the handler then yields the probes
            batch -> result.complete(toActivationResponse(batch, physicalTenantId)),
            error -> result.complete(RestErrorMapper.mapErrorToResponse(error)));
    return RequestExecutor.toDeferredResult(result, -1L);
  }

  @CamundaPostMapping(path = "/{probeKey}/answer")
  public CompletableFuture<ResponseEntity<Object>> answerProbe(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long probeKey,
      @RequestBody final AnswerProbeBody body) {
    final ProbeOutcome outcome;
    try {
      outcome = ProbeOutcome.valueOf(String.valueOf(body.outcome()));
    } catch (final IllegalArgumentException e) {
      return RestErrorMapper.mapProblemToCompletedResponse(
          ProblemDetail.forStatusAndDetail(
              HttpStatus.BAD_REQUEST,
              "Expected the outcome to be one of [OK, UNSUPPORTED, ERROR], but it was '%s'."
                  .formatted(body.outcome())));
    }
    final var request = new ProbeAnswerRequest(probeKey, outcome, body.data(), body.message());
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> probeServices(physicalTenantId).answerProbe(request, authentication));
  }

  private ProbeServices probeServices(final String physicalTenantId) {
    return ProbeServices.of(serviceRegistry.expressionServices(physicalTenantId));
  }

  private static String validate(final CreateProbeBody body) {
    if (body.probeId() == null || body.probeId().isBlank()) {
      return "Expected a probe ID, but none was given.";
    }
    if (body.input() == null || body.input().isBlank()) {
      return "Expected an input, but none was given.";
    }
    final var timeout = body.requestTimeout();
    if (timeout != null && (timeout < 0 || timeout > ProbeServices.MAX_REQUEST_TIMEOUT_MS)) {
      return "Expected a request timeout between 0 and %d ms, but it was %d."
          .formatted(ProbeServices.MAX_REQUEST_TIMEOUT_MS, timeout);
    }
    return null;
  }

  /** The answer envelope, or a problem named after the reason the probe ended without one. */
  private static ResponseEntity<Object> toResponse(final ProbeResult result) {
    final var probe = result.probe();
    final var endReason = probe.getEndReason();
    if (endReason == ProbeEndReason.UNSPECIFIED) {
      final var envelope = new LinkedHashMap<String, Object>();
      envelope.put("outcome", probe.getOutcome().name());
      envelope.put("worker", probe.getWorker());
      if (probe.getOutcome() == ProbeOutcome.OK) {
        envelope.put("data", probe.getData());
      }
      if (probe.getOutcome() == ProbeOutcome.ERROR) {
        envelope.put("message", probe.getMessage());
      }
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(envelope);
    }

    final var status =
        switch (endReason) {
          case NO_WORKER -> HttpStatus.NOT_FOUND;
          case SECRET_RESOLUTION_ERROR -> HttpStatus.BAD_REQUEST;
          case WORKER_DID_NOT_ANSWER, SECRET_RESOLUTION_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
          case UNSPECIFIED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    final var problem = ProblemDetail.forStatusAndDetail(status, probe.getMessage());
    problem.setTitle(endReason.name());
    return RestErrorMapper.mapProblemToResponse(problem);
  }

  public record CreateProbeBody(
      String probeId, String input, String tenantId, Long requestTimeout) {}

  public record AnswerProbeBody(String outcome, Map<String, Object> data, String message) {}

  public record ActivateProbesBody(
      String probeId,
      String worker,
      Integer maxProbesToActivate,
      Long requestTimeout,
      List<String> tenantIds,
      String tenantFilter) {}

  private static DeferredResult<ResponseEntity<Object>> badRequest(final String detail) {
    return RequestExecutor.toDeferredResult(
        RestErrorMapper.mapProblemToCompletedResponse(
            ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)),
        -1L);
  }

  /** The activated probes, with the secret values the engine injected into the response. */
  private static ResponseEntity<Object> toActivationResponse(
      final ProbeBatchRecord batch, final String physicalTenantId) {
    final List<Map<String, Object>> probes = new ArrayList<>();
    int index = 0;
    for (final ProbeRecord probe : batch.probes()) {
      final var activated = new LinkedHashMap<String, Object>();
      activated.put("probeKey", String.valueOf(batch.probeKeys().get(index++).getValue()));
      activated.put("probeId", probe.getProbeId());
      activated.put("worker", probe.getWorker());
      activated.put("deadline", probe.getDeadline());
      activated.put("tenantId", probe.getTenantId());
      activated.put("physicalTenantId", physicalTenantId);
      activated.put("input", probe.getInput());
      probes.add(activated);
    }
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("probes", probes));
  }
}
