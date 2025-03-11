/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.MappingQuery;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.zeebe.gateway.protocol.rest.MappingResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/mapping-rules")
public class MappingController {
  private final MappingServices mappingServices;

  public MappingController(final MappingServices mappingServices) {
    this.mappingServices = mappingServices;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> create(
      @RequestBody final MappingRuleCreateRequest mappingRequest) {
    return RequestMapper.toMappingDTO(mappingRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createMapping);
  }

  private CompletableFuture<ResponseEntity<Object>> createMapping(final MappingDTO request) {
    return RequestMapper.executeServiceMethod(
        () ->
            mappingServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createMapping(request),
        ResponseMapper::toMappingCreateResponse);
  }

  @CamundaDeleteMapping(path = "/{mappingId}")
  public CompletableFuture<ResponseEntity<Object>> deleteMapping(
      @PathVariable final String mappingId) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            mappingServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteMapping(mappingId));
  }

  @CamundaGetMapping(path = "/{mappingKey}")
  public ResponseEntity<MappingResult> getMapping(@PathVariable final long mappingKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toMapping(
                  mappingServices
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getMapping(mappingKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<MappingSearchQueryResult> searchMappings(
      @RequestBody(required = false) final MappingSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<MappingSearchQueryResult> search(final MappingQuery query) {
    try {
      final var result =
          mappingServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
