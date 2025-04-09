/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.SetVariableRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
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
  private final ProcessCache processCache;

  public ElementInstanceController(
      final ElementInstanceServices elementInstanceServices, final ProcessCache processCache) {
    this.elementInstanceServices = elementInstanceServices;
    this.processCache = processCache;
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
                .withAuthentication(RequestMapper.getAuthentication())
                .setVariables(variablesRequest));
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ElementInstanceSearchQueryResult> searchElementInstances(
      @RequestBody(required = false) final ElementInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toElementInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaGetMapping(path = "/{elementInstanceKey}")
  public ResponseEntity<ElementInstanceResult> getByKey(
      @PathVariable("elementInstanceKey") final Long elementInstanceKey) {
    try {
      final FlowNodeInstanceEntity element =
          elementInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .getByKey(elementInstanceKey);
      final var name = processCache.getElementName(element);
      return ResponseEntity.ok().body(SearchQueryResponseMapper.toElementInstance(element, name));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<ElementInstanceSearchQueryResult> search(
      final FlowNodeInstanceQuery query) {
    try {
      final var result =
          elementInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      final var processCacheItems = processCache.getElementNames(result.items());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toElementInstanceSearchQueryResponse(
              result, processCacheItems));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
