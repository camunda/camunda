/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQueryResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQueryResult;
import io.camunda.gateway.protocol.model.IncidentResolutionRequest;
import io.camunda.gateway.protocol.model.IncidentResult;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.IncidentSearchQueryResult;
import io.camunda.search.query.IncidentQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.validation.ValidationException;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("v2/incidents")
public class IncidentController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public IncidentController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/{incidentKey}/resolution")
  public CompletableFuture<ResponseEntity<Object>> incidentResolution(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long incidentKey,
      @RequestBody(required = false) final IncidentResolutionRequest incidentResolutionRequest) {
    final Long operationReference =
        incidentResolutionRequest == null
            ? null
            : incidentResolutionRequest.getOperationReference();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .incidentServices(physicalTenantId)
                .resolveIncident(
                    incidentKey,
                    operationReference,
                    authenticationProvider.getCamundaAuthentication()));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final IncidentSearchQuery query) {
    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(serviceRegistry.incidentServices(physicalTenantId), q));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{incidentKey}")
  public ResponseEntity<IncidentResult> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("incidentKey") final Long incidentKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toIncident(
                  serviceRegistry
                      .incidentServices(physicalTenantId)
                      .getByKey(incidentKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/process-instances-by-error")
  public ResponseEntity<IncidentProcessInstanceStatisticsByErrorQueryResult>
      processInstanceStatisticsByError(
          @PhysicalTenantId final String physicalTenantId,
          @RequestBody(required = false)
              final IncidentProcessInstanceStatisticsByErrorQuery query) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByErrorQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q ->
                getIncidentProcessInstanceStatisticsByError(
                    serviceRegistry.incidentServices(physicalTenantId), q));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/process-instances-by-definition")
  public ResponseEntity<IncidentProcessInstanceStatisticsByDefinitionQueryResult>
      incidentProcessInstanceStatisticsByDefinition(
          @PhysicalTenantId final String physicalTenantId,
          @RequestBody() final IncidentProcessInstanceStatisticsByDefinitionQuery query) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByDefinitionQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q ->
                searchIncidentProcessInstanceStatisticsByDefinition(
                    serviceRegistry.incidentServices(physicalTenantId), q));
  }

  private ResponseEntity<IncidentSearchQueryResult> search(
      final IncidentServices incidentServices, final IncidentQuery query) {
    try {
      final var result =
          incidentServices.search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentProcessInstanceStatisticsByErrorQueryResult>
      getIncidentProcessInstanceStatisticsByError(
          final IncidentServices incidentServices,
          final io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery query) {
    try {
      final var result =
          incidentServices.incidentProcessInstanceStatisticsByError(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByErrorResult(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Statistics Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentProcessInstanceStatisticsByDefinitionQueryResult>
      searchIncidentProcessInstanceStatisticsByDefinition(
          final IncidentServices incidentServices,
          final io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery query) {
    try {
      final var result =
          incidentServices.searchIncidentProcessInstanceStatisticsByDefinition(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByDefinitionQueryResult(
              result));
    } catch (final ValidationException e) {
      final var problemDetail =
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Process Instance Statistics By Definition Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
