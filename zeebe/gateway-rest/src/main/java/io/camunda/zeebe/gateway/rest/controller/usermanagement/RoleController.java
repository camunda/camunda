/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.search.query.RoleQuery;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleUpdateRequest;
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
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/roles")
public class RoleController {
  private final RoleServices roleServices;

  public RoleController(final RoleServices roleServices) {
    this.roleServices = roleServices;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createRole(
      @RequestBody final RoleCreateRequest createRoleRequest) {
    return RequestMapper.toRoleCreateRequest(createRoleRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createRole);
  }

  private CompletableFuture<ResponseEntity<Object>> createRole(
      final CreateRoleRequest createRoleRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            roleServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createRole(createRoleRequest),
        ResponseMapper::toRoleCreateResponse);
  }

  @CamundaPutMapping(path = "/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> updateRole(
      @PathVariable final String roleId, @RequestBody final RoleUpdateRequest roleUpdateRequest) {
    return RequestMapper.toRoleUpdateRequest(roleUpdateRequest, roleId)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateRole);
  }

  public CompletableFuture<ResponseEntity<Object>> updateRole(
      final UpdateRoleRequest updateRoleRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            roleServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateRole(updateRoleRequest),
        ResponseMapper::toRoleUpdateResponse);
  }

  @CamundaDeleteMapping(path = "/{roleId}")
  public CompletableFuture<ResponseEntity<Object>> deleteRole(@PathVariable final String roleId) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            roleServices.withAuthentication(RequestMapper.getAuthentication()).deleteRole(roleId));
  }

  @CamundaGetMapping(path = "/{roleKey}")
  public ResponseEntity<Object> getRole(@PathVariable final long roleKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toRole(
                  roleServices
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getRole(roleKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<RoleSearchQueryResult> searchRoles(
      @RequestBody(required = false) final RoleSearchQueryRequest query) {
    return SearchQueryRequestMapper.toRoleQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaPutMapping(
      path = "/{roleId}/users/{username}",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> addRole(
      @PathVariable final String roleId, @PathVariable final String username) {
    return RequestMapper.executeServiceMethodWithAcceptedResult(
        () ->
            roleServices
                .withAuthentication(RequestMapper.getAuthentication())
                .addMember(roleId, EntityType.USER, username));
  }

  private ResponseEntity<RoleSearchQueryResult> search(final RoleQuery query) {
    try {
      final var result =
          roleServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }
}
