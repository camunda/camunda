/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.MappingRuleQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/mapping-rules")
public class MappingRuleController {
  private final MappingRuleServices mappingServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public MappingRuleController(
      final MappingRuleServices mappingServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.mappingServices = mappingServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> create(
      @RequestBody final MappingRuleCreateRequest mappingRequest) {
    return RequestMapper.toMappingDTO(mappingRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createMapping);
  }

  @CamundaPutMapping(path = "/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> update(
      @PathVariable final String mappingRuleId,
      @RequestBody final MappingRuleUpdateRequest mappingRequest) {
    return RequestMapper.toMappingDTO(mappingRuleId, mappingRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateMapping);
  }

  @CamundaDeleteMapping(path = "/{mappingRuleId}")
  public CompletableFuture<ResponseEntity<Object>> deleteMapping(
      @PathVariable final String mappingRuleId) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            mappingServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteMappingRule(mappingRuleId));
  }

  @CamundaGetMapping(path = "/{mappingRuleId}")
  public ResponseEntity<MappingRuleResult> getMappingRule(
      @PathVariable final String mappingRuleId) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toMapping(
                  mappingServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getMappingRule(mappingRuleId)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<MappingRuleSearchQueryResult> searchMappings(
      @RequestBody(required = false) final MappingRuleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private CompletableFuture<ResponseEntity<Object>> createMapping(final MappingRuleDTO request) {
    return RequestMapper.executeServiceMethod(
        () ->
            mappingServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createMappingRule(request),
        ResponseMapper::toMappingCreateResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> updateMapping(final MappingRuleDTO request) {
    return RequestMapper.executeServiceMethod(
        () ->
            mappingServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateMappingRule(request),
        ResponseMapper::toMappingUpdateResponse);
  }

  private ResponseEntity<MappingRuleSearchQueryResult> search(final MappingRuleQuery query) {
    try {
      final var result =
          mappingServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
