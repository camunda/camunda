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
import io.camunda.service.FlowNodeInstanceServices;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/flownode-instances")
public class FlowNodeInstanceController {

  private final FlowNodeInstanceServices flownodeInstanceServices;
  private final ProcessCache processCache;

  public FlowNodeInstanceController(
      final FlowNodeInstanceServices flownodeInstanceServices, final ProcessCache processCache) {
    this.flownodeInstanceServices = flownodeInstanceServices;
    this.processCache = processCache;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ElementInstanceSearchQueryResult> searchFlownodeInstances(
      @RequestBody(required = false) final ElementInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toFlownodeInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaGetMapping(path = "/{flowNodeInstanceKey}")
  public ResponseEntity<ElementInstanceResult> getByKey(
      @PathVariable("flowNodeInstanceKey") final Long flowNodeInstanceKey) {
    try {
      final FlowNodeInstanceEntity flowNode =
          flownodeInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .getByKey(flowNodeInstanceKey);
      final var name = processCache.getFlowNodeName(flowNode);
      return ResponseEntity.ok().body(SearchQueryResponseMapper.toFlowNodeInstance(flowNode, name));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<ElementInstanceSearchQueryResult> search(
      final FlowNodeInstanceQuery query) {
    try {
      final var result =
          flownodeInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      final var processCacheItems = processCache.getFlowNodeNames(result.items());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toFlowNodeInstanceSearchQueryResponse(
              result, processCacheItems));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
