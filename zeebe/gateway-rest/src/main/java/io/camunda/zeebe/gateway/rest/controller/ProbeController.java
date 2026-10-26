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
import io.camunda.service.ProbeServices.ProbeAnswerRequest;
import io.camunda.service.ProbeServices.ProbeCreateRequest;
import io.camunda.service.ProbeServices.ProbeResult;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOperation;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

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

  public ProbeController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
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
            ProbeOperation.valueOf(body.operation()),
            body.template(),
            body.input(),
            body.tenantId(),
            body.requestTimeout());
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
    if (body.operation() == null
        || Arrays.stream(ProbeOperation.values())
            .noneMatch(op -> op.name().equals(body.operation()))) {
      return "Expected the operation to be one of %s, but it was '%s'."
          .formatted(Arrays.toString(ProbeOperation.values()), body.operation());
    }
    if (body.template() == null || body.template().isBlank()) {
      return "Expected a template, but none was given.";
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
      String operation, String template, String input, String tenantId, Long requestTimeout) {}

  public record AnswerProbeBody(String outcome, Map<String, Object> data, String message) {}
}
