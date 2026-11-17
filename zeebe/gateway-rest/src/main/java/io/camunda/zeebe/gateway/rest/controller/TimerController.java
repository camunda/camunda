/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.TimerSearchQuery;
import io.camunda.gateway.protocol.model.TimerSearchQueryResult;
import io.camunda.gateway.protocol.model.TimerStateEnum;
import io.camunda.search.query.ElementInstanceWaitStateQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/timers")
public class TimerController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public TimerController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<TimerSearchQueryResult> searchTimers(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final TimerSearchQuery query) {
    final var state =
        query == null || query.getFilter() == null ? null : query.getFilter().getState();
    if (state != null && state != TimerStateEnum.CREATED) {
      // only waiting timers are searchable
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toTimerSearchQueryResult(SearchQueryResult.empty()));
    }
    return SearchQueryRequestMapper.toTimerWaitStateQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(physicalTenantId, q));
  }

  private ResponseEntity<TimerSearchQueryResult> search(
      final String physicalTenantId, final ElementInstanceWaitStateQuery query) {
    try {
      final var result =
          serviceRegistry
              .elementInstanceServices(physicalTenantId)
              .searchWaitStates(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toTimerSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaPostMapping(
      path = "/{timerKey}/trigger",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> triggerTimer(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long timerKey) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .timerServices(physicalTenantId)
                .triggerTimer(timerKey, authenticationProvider.getCamundaAuthentication()));
  }
}
