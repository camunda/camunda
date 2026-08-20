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
import io.camunda.gateway.protocol.model.DecisionRequirementsResult;
import io.camunda.gateway.protocol.model.DecisionRequirementsSearchQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/decision-requirements")
public class DecisionRequirementsController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public DecisionRequirementsController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<Object> searchDecisionRequirements(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final DecisionRequirementsSearchQuery query) {
    return SearchQueryRequestMapper.toDecisionRequirementsQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(physicalTenantId, q));
  }

  private ResponseEntity<Object> search(
      final String physicalTenantId, final DecisionRequirementsQuery query) {
    final var decisionRequirementsServices =
        serviceRegistry.decisionRequirementsServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = decisionRequirementsServices.search(query, authentication);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionRequirementsSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{decisionRequirementsKey}")
  public ResponseEntity<DecisionRequirementsResult> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("decisionRequirementsKey") final Long decisionRequirementsKey) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toDecisionRequirements(
                  serviceRegistry
                      .decisionRequirementsServices(physicalTenantId)
                      .getByKey(decisionRequirementsKey, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(
      path = "/{decisionRequirementsKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getDecisionRequirementsXml(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("decisionRequirementsKey") final Long decisionRequirementsKey) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
          .body(
              serviceRegistry
                  .decisionRequirementsServices(physicalTenantId)
                  .getDecisionRequirementsXml(decisionRequirementsKey, authentication));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
