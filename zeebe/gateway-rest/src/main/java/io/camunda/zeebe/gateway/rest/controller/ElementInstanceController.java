/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.protocol.model.ElementInstanceResult;
import io.camunda.gateway.protocol.model.ElementInstanceSearchQuery;
import io.camunda.gateway.protocol.model.ElementInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.IncidentSearchQueryResult;
import io.camunda.gateway.protocol.model.SetVariableRequest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/element-instances")
public class ElementInstanceController {

  private final ElementInstanceServices elementInstanceServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ElementInstanceController(
      final ElementInstanceServices elementInstanceServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.elementInstanceServices = elementInstanceServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPutMapping(
      path = "/{elementInstanceKey}/variables",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> setVariables(
      @PathVariable final long elementInstanceKey,
      @RequestBody final SetVariableRequest variableRequest) {
    return RequestMapper.toVariableRequest(variableRequest, elementInstanceKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::setVariables);
  }

  private CompletableFuture<ResponseEntity<Object>> setVariables(
      final SetVariablesRequest variablesRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            elementInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .setVariables(variablesRequest));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ElementInstanceSearchQueryResult> searchElementInstances(
      @RequestBody(required = false) final ElementInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toElementInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{elementInstanceKey}")
  public ResponseEntity<ElementInstanceResult> getByKey(
      @PathVariable("elementInstanceKey") final Long elementInstanceKey) {
    try {
      final FlowNodeInstanceEntity element =
          elementInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .getByKey(elementInstanceKey);

      return ResponseEntity.ok().body(SearchQueryResponseMapper.toElementInstance(element));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{elementInstanceKey}/incidents/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidentsForElementInstance(
      @PathVariable("elementInstanceKey") final long elementInstanceKey,
      @RequestBody(required = false) final IncidentSearchQuery query) {

    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            incidentQuery -> searchIncidents(elementInstanceKey, incidentQuery));
  }

  private ResponseEntity<ElementInstanceSearchQueryResult> search(
      final FlowNodeInstanceQuery query) {
    try {
      final var result =
          elementInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);

      return ResponseEntity.ok(
          SearchQueryResponseMapper.toElementInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      final long elementInstanceKey, final IncidentQuery query) {
    try {
      final var result =
          elementInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchIncidents(elementInstanceKey, query);

      return ResponseEntity.ok(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
