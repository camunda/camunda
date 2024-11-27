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
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceItem;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/flownode-instances")
public class FlowNodeInstanceQueryController {

  private final FlowNodeInstanceServices flownodeInstanceServices;
  private final ProcessCache processCache;

  public FlowNodeInstanceQueryController(
      final FlowNodeInstanceServices flownodeInstanceServices, final ProcessCache processCache) {
    this.flownodeInstanceServices = flownodeInstanceServices;
    this.processCache = processCache;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FlowNodeInstanceSearchQueryResponse> searchFlownodeInstances(
      @RequestBody(required = false) final FlowNodeInstanceSearchQueryRequest query) {
    return SearchQueryRequestMapper.toFlownodeInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @GetMapping(
      path = "/{flowNodeInstanceKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<FlowNodeInstanceItem> getByKey(
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

  private ResponseEntity<FlowNodeInstanceSearchQueryResponse> search(
      final FlowNodeInstanceQuery query) {
    try {
      final var result =
          flownodeInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      final var processCacheItems =
          processCache.getCacheItems(
              result.items().stream().map(FlowNodeInstanceEntity::processDefinitionKey).toList());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toFlowNodeInstanceSearchQueryResponse(
              result, processCacheItems));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
