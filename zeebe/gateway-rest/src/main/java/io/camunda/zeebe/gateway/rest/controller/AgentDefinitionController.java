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
import io.camunda.gateway.protocol.model.AgentDefinitionResult;
import io.camunda.gateway.protocol.model.AgentDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.AgentDefinitionSearchQueryResult;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/agent-definitions")
public class AgentDefinitionController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public AgentDefinitionController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{agentDefinitionKey}")
  public ResponseEntity<AgentDefinitionResult> getAgentDefinition(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("agentDefinitionKey") final long agentDefinitionKey) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toAgentDefinition(
              serviceRegistry
                  .agentDefinitionServices(physicalTenantId)
                  .getByKey(
                      agentDefinitionKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<AgentDefinitionSearchQueryResult> searchAgentDefinitions(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final AgentDefinitionSearchQuery request) {
    return SearchQueryRequestMapper.toAgentDefinitionQuery(request)
        .fold(RestErrorMapper::mapProblemToResponse, query -> search(physicalTenantId, query));
  }

  private ResponseEntity<AgentDefinitionSearchQueryResult> search(
      final String physicalTenantId, final AgentDefinitionQuery query) {
    final var agentDefinitionServices = serviceRegistry.agentDefinitionServices(physicalTenantId);
    try {
      final var result =
          agentDefinitionServices.search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toAgentDefinitionSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
