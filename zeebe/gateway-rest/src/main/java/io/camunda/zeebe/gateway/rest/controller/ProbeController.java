/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.mapper.ProbeMapper;
import io.camunda.gateway.protocol.model.ProbeActivationRequest;
import io.camunda.gateway.protocol.model.ProbeAnswerRequest;
import io.camunda.gateway.protocol.model.ProbeCreationRequest;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ProbeServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.async.DeferredResult;

/**
 * Probes (alpha, spec section 4): a client asks a worker a short question and waits for the answer
 * in the same request; workers collect probes with long polling and answer each one.
 */
@NullMarked
@CamundaRestController
@RequestMapping("/v2/probes")
public class ProbeController {

  /**
   * No servlet async timeout. A probe request ends with the probe, which its deadline bounds, and
   * the gateway waits until 500 ms after the deadline (section 10.1); a poll ends at its own
   * request timeout, as for jobs. The container's default timeout would cut either short.
   */
  private static final long NO_ASYNC_TIMEOUT = -1L;

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public ProbeController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @CamundaPostMapping
  public DeferredResult<ResponseEntity<Object>> createProbe(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProbeCreationRequest request) {
    return RequestExecutor.toDeferredResult(
        ProbeMapper.toProbeCreateRequest(request, multiTenancyCfg.isChecksEnabled())
            .fold(
                RestErrorMapper::mapProblemToCompletedResponse,
                r -> createProbe(physicalTenantId, r)),
        NO_ASYNC_TIMEOUT);
  }

  @CamundaPostMapping(path = "/activation")
  public DeferredResult<ResponseEntity<Object>> activateProbes(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProbeActivationRequest request) {
    return ProbeMapper.toProbeActivationRequest(request, multiTenancyCfg.isChecksEnabled())
        .fold(
            problem ->
                RequestExecutor.toDeferredResult(
                    RestErrorMapper.mapProblemToCompletedResponse(problem), NO_ASYNC_TIMEOUT),
            r -> activateProbes(physicalTenantId, r));
  }

  @CamundaPostMapping(path = "/{probeKey}/answer")
  public CompletableFuture<ResponseEntity<Object>> answerProbe(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long probeKey,
      @RequestBody final ProbeAnswerRequest request) {
    return ProbeMapper.toProbeAnswerRequest(request, probeKey)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse, r -> answerProbe(physicalTenantId, r));
  }

  private CompletableFuture<ResponseEntity<Object>> createProbe(
      final String physicalTenantId, final ProbeServices.ProbeCreateRequest request) {
    return serviceRegistry
        .probeServices(physicalTenantId)
        .createProbe(request, authenticationProvider.getCamundaAuthentication())
        .handleAsync(
            (result, error) ->
                error != null
                    ? RestErrorMapper.mapProblemToResponse(
                        ProbeMapper.mapErrorToProblem(error, ProbeMapper.PROBE_TOO_LARGE))
                    : ProbeMapper.toProbeResult(result)
                        .fold(
                            RestErrorMapper::mapProblemToResponse,
                            answer ->
                                ResponseEntity.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(answer)));
  }

  private DeferredResult<ResponseEntity<Object>> activateProbes(
      final String physicalTenantId, final ProbeServices.ProbeActivationRequest request) {
    final var result = new CompletableFuture<ResponseEntity<Object>>();
    final var responseObserver = new ProbeActivationResponseObserver(result, physicalTenantId);
    serviceRegistry
        .probeServices(physicalTenantId)
        .activateProbes(
            request,
            responseObserver,
            responseObserver::setCancelationHandler,
            authenticationProvider.getCamundaAuthentication());
    final var response =
        result.handleAsync(
            (res, ex) -> {
              // a poll that is still held ends here; one that was answered is not affected
              responseObserver.invokeCancelationHandler();
              return res;
            });
    final var deferred = RequestExecutor.toDeferredResult(response, NO_ASYNC_TIMEOUT);
    // the worker's request is gone, for example its connection broke: the poll is cancelled, and
    // the probes it collected but could not deliver are yielded (D41)
    deferred.onError(error -> result.cancel(false));
    return deferred;
  }

  private CompletableFuture<ResponseEntity<Object>> answerProbe(
      final String physicalTenantId, final ProbeServices.ProbeAnswerRequest request) {
    return serviceRegistry
        .probeServices(physicalTenantId)
        .answerProbe(request, authenticationProvider.getCamundaAuthentication())
        .handleAsync(
            (ignored, error) ->
                error != null
                    ? RestErrorMapper.mapProblemToResponse(
                        ProbeMapper.mapErrorToProblem(error, ProbeMapper.ANSWER_TOO_LARGE))
                    : ResponseEntity.noContent().build());
  }
}
