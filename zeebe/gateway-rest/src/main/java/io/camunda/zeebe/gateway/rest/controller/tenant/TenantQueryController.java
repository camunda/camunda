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
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.TenantItem;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestQueryController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CamundaRestQueryController
@RequestMapping("/v2/tenants")
public class TenantQueryController {
  private final TenantServices tenantServices;
  private final UserServices userServices;

  public TenantQueryController(
      final TenantServices tenantServices, final UserServices userServices) {
    this.tenantServices = tenantServices;
    this.userServices = userServices;
  }

  @GetMapping(
      path = "/{tenantKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<TenantItem> getTenant(@PathVariable final long tenantKey) {
    try {
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toTenant(tenantServices.getByKey(tenantKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TenantSearchQueryResponse> searchTenants(
      @RequestBody(required = false) final TenantSearchQueryRequest query) {
    return SearchQueryRequestMapper.toTenantQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @PostMapping(
      path = "/{tenantKey}/users/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserSearchResponse> listUsersOfTenant(
      @PathVariable final long tenantKey,
      @RequestBody(required = false) final UserSearchQueryRequest query) {
    try {
      // Retrieve tenant entity and its assigned user keys
      final var tenantEntity = tenantServices.getByKey(tenantKey);
      final var assignedUserKeys = tenantEntity.assignedMemberKeys();

      // Return 404 if no users are assigned to the tenant
      if (assignedUserKeys == null || assignedUserKeys.isEmpty()) {
        final var problemDetail =
            RestErrorMapper.createProblemDetail(
                HttpStatus.NOT_FOUND,
                "No users assigned to tenant with key: " + tenantKey,
                "NotFound");
        return RestErrorMapper.mapProblemToResponse(problemDetail);
      }

      final var userQueryEither = SearchQueryRequestMapper.toUserQuery(query);
      final var mergedQueryEither =
          userQueryEither.map(
              userQuery ->
                  SearchQueryRequestMapper.mergeWithTenantKeys(userQuery, assignedUserKeys));

      return mergedQueryEither.fold(
          RestErrorMapper::mapProblemToResponse,
          q -> {
            final var result = userServices.search(q);
            if (result.items() == null || result.items().isEmpty()) {
              // Return 404 if no users are found after the search
              final var problemDetail =
                  RestErrorMapper.createProblemDetail(
                      HttpStatus.NOT_FOUND,
                      "No users found for tenant with key: " + tenantKey,
                      "NotFound");
              return RestErrorMapper.mapProblemToResponse(problemDetail);
            }
            return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
          });
    } catch (final Exception exception) {
      return mapErrorToResponse(exception);
    }
  }

  private ResponseEntity<TenantSearchQueryResponse> search(final TenantQuery query) {
    try {
      final var result = tenantServices.search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toTenantSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
