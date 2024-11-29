/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.TenantQuery;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestQueryController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CamundaRestQueryController
@RequestMapping("/v2/tenants")
public class TenantQueryController {
  private final TenantServices tenantServices;

  public TenantQueryController(final TenantServices tenantServices) {
    this.tenantServices = tenantServices;
  }

  @GetMapping(
      path = "/{tenantKey}",
      produces = {
        MediaType.APPLICATION_JSON_VALUE,
        RequestMapper.MEDIA_TYPE_KEYS_NUMBER,
        MediaType.APPLICATION_PROBLEM_JSON_VALUE
      })
  public ResponseEntity<?> getTenant(@PathVariable final long tenantKey) {
    try {
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toTenant(tenantServices.getByKey(tenantKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @GetMapping(
      path = "/{tenantKey}",
      produces = {RequestMapper.MEDIA_TYPE_KEYS_STRING, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<?> getTenantStringKeys(@PathVariable final long tenantKey) {
    try {
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toTenantStringKeys(tenantServices.getByKey(tenantKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> searchTenants(
      @RequestBody(required = false) final TenantSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<?> search(final TenantQuery query) {
    try {
      final var result = tenantServices.search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
