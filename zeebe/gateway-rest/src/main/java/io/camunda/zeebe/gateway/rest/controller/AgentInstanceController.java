/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.mapper.AgentInstanceMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.AgentInstanceRequestValidator;
import io.camunda.gateway.protocol.model.AgentInstanceCreationRequest;
import io.camunda.gateway.protocol.model.AgentInstanceResult;
import io.camunda.gateway.protocol.model.AgentInstanceSearchQuery;
import io.camunda.gateway.protocol.model.AgentInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.AgentInstanceServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/agent-instances")
public class AgentInstanceController {

  private final AgentInstanceServices agentInstanceServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final AgentInstanceMapper mapper;

  public AgentInstanceController(
      final AgentInstanceServices agentInstanceServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.agentInstanceServices = agentInstanceServices;
    this.authenticationProvider = authenticationProvider;
    mapper = new AgentInstanceMapper(new AgentInstanceRequestValidator());
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createAgentInstance(
      @RequestBody final AgentInstanceCreationRequest request) {
    return mapper
        .toCreateAgentInstanceRecord(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::create);
  }

  @CamundaPatchMapping(path = "/{agentInstanceKey}")
  public CompletableFuture<ResponseEntity<Object>> updateAgentInstance(
      @PathVariable final String agentInstanceKey,
      @RequestBody final AgentInstanceUpdateRequest request) {
    return mapper
        .toUpdateAgentInstanceRecord(agentInstanceKey, request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::update);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{agentInstanceKey}")
  public ResponseEntity<AgentInstanceResult> getAgentInstance(
      @PathVariable("agentInstanceKey") final long agentInstanceKey) {
    try {
      final var agentInstance =
          agentInstanceServices.getByKey(
              agentInstanceKey, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toAgentInstanceResult(agentInstance));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<AgentInstanceSearchQueryResult> searchAgentInstances(
      @RequestBody(required = false) final AgentInstanceSearchQuery request) {
    return SearchQueryRequestMapper.toAgentInstanceQuery(request)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private CompletableFuture<ResponseEntity<Object>> create(final AgentInstanceRecord record) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> agentInstanceServices.createAgentInstance(record, authentication),
        mapper::toAgentInstanceCreationResult,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> update(final AgentInstanceRecord record) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> agentInstanceServices.updateAgentInstance(record, authentication));
  }

  private ResponseEntity<AgentInstanceSearchQueryResult> search(final AgentInstanceQuery query) {
    try {
      final var result =
          agentInstanceServices.search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toAgentInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
