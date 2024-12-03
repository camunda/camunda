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
import io.camunda.zeebe.gateway.protocol.rest.MappingItem;
import io.camunda.zeebe.gateway.protocol.rest.MappingSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestQueryController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CamundaRestQueryController
@RequestMapping("/v2/mapping-rules")
public class MappingQueryController {
  private final MappingServices mappingServices;

  public MappingQueryController(final MappingServices mappingServices) {
    this.mappingServices = mappingServices;
  }

  @GetMapping(
      path = "/{mappingKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<MappingItem> getMapping(@PathVariable final long mappingKey) {
    try {
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toMapping(mappingServices.getMapping(mappingKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TenantSearchQueryResponse> searchMappings(
      @RequestBody(required = false) final MappingSearchQueryRequest query) {
    return SearchQueryRequestMapper.toMappingQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<TenantSearchQueryResponse> search(final MappingQuery query) {
    try {
      final var result = mappingServices.search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
