/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.ElementInstanceResult;
import io.camunda.gateway.protocol.model.ElementInstanceSearchQuery;
import io.camunda.gateway.protocol.model.ElementInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.ElementInstanceWaitStateQueryResult;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.IncidentSearchQueryResult;
import io.camunda.gateway.protocol.model.SetVariableRequest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.ElementInstanceWaitStateQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/element-instances")
public class ElementInstanceController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ElementInstanceController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPutMapping(
      path = "/{elementInstanceKey}/variables",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> setVariables(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long elementInstanceKey,
      @RequestBody final SetVariableRequest variableRequest) {
    return RequestMapper.toVariableRequest(variableRequest, elementInstanceKey)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                setVariables(serviceRegistry.elementInstanceServices(physicalTenantId), mapped));
  }

  private CompletableFuture<ResponseEntity<Object>> setVariables(
      final ElementInstanceServices elementInstanceServices,
      final SetVariablesRequest variablesRequest) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            elementInstanceServices.setVariables(
                variablesRequest, authenticationProvider.getCamundaAuthentication()));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/wait-states/search")
  public ResponseEntity<ElementInstanceWaitStateQueryResult> searchElementInstanceWaitStates(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false)
          final io.camunda.gateway.protocol.model.ElementInstanceWaitStateQuery query) {
    return SearchQueryRequestMapper.toElementInstanceWaitStateQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> searchWaitStates(serviceRegistry.elementInstanceServices(physicalTenantId), q));
  }

  private ResponseEntity<ElementInstanceWaitStateQueryResult> searchWaitStates(
      final ElementInstanceServices elementInstanceServices,
      final ElementInstanceWaitStateQuery query) {

    try {
      final var result =
          elementInstanceServices.searchWaitStates(
              query, authenticationProvider.getCamundaAuthentication());

      return ResponseEntity.ok(
          SearchQueryResponseMapper.toElementInstanceWaitStateQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ElementInstanceSearchQueryResult> searchElementInstances(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final ElementInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toElementInstanceQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(serviceRegistry.elementInstanceServices(physicalTenantId), q));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{elementInstanceKey}")
  public ResponseEntity<ElementInstanceResult> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("elementInstanceKey") final Long elementInstanceKey) {
    try {
      final FlowNodeInstanceEntity element =
          serviceRegistry
              .elementInstanceServices(physicalTenantId)
              .getByKey(elementInstanceKey, authenticationProvider.getCamundaAuthentication());

      return ResponseEntity.ok().body(SearchQueryResponseMapper.toElementInstance(element));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{elementInstanceKey}/incidents/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidentsForElementInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("elementInstanceKey") final long elementInstanceKey,
      @RequestBody(required = false) final IncidentSearchQuery query) {

    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            incidentQuery ->
                searchIncidents(
                    serviceRegistry.elementInstanceServices(physicalTenantId),
                    elementInstanceKey,
                    incidentQuery));
  }

  private ResponseEntity<ElementInstanceSearchQueryResult> search(
      final ElementInstanceServices elementInstanceServices, final FlowNodeInstanceQuery query) {
    try {
      final var result =
          elementInstanceServices.search(query, authenticationProvider.getCamundaAuthentication());

      return ResponseEntity.ok(
          SearchQueryResponseMapper.toElementInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      final ElementInstanceServices elementInstanceServices,
      final long elementInstanceKey,
      final IncidentQuery query) {
    try {
      final var result =
          elementInstanceServices.searchIncidents(
              elementInstanceKey, query, authenticationProvider.getCamundaAuthentication());

      return ResponseEntity.ok(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
